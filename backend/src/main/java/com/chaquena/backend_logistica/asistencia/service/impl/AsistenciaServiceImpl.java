package com.chaquena.backend_logistica.asistencia.service.impl;

import com.chaquena.backend_logistica.asistencia.domain.Marcacion;
import com.chaquena.backend_logistica.asistencia.domain.Turno;
import com.chaquena.backend_logistica.asistencia.dto.AsistenciaDelDiaDto;
import com.chaquena.backend_logistica.asistencia.dto.MiAsistenciaDto;
import com.chaquena.backend_logistica.asistencia.dto.TurnoDto;
import com.chaquena.backend_logistica.asistencia.repository.MarcacionRepository;
import com.chaquena.backend_logistica.asistencia.repository.TurnoRepository;
import com.chaquena.backend_logistica.asistencia.service.AsistenciaService;
import com.chaquena.backend_logistica.asistencia.service.ReglaAsistencia;
import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.service.TrabajadorContexto;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AsistenciaServiceImpl implements AsistenciaService {

    private static final DateTimeFormatter HORA_Y_DIA = DateTimeFormatter.ofPattern("HH:mm 'del' dd/MM");

    private final MarcacionRepository marcacionRepository;
    private final TurnoRepository turnoRepository;
    private final TrabajadorContexto trabajadorContexto;

    /**
     * Una entrada abierta no se cierra sola al volver a entrar: poner una
     * salida que nadie marco inventaria las horas de ese dia.
     */
    @Override
    @Transactional
    public MiAsistenciaDto marcarEntrada() {
        Trabajador yo = yo();
        marcacionRepository.findFirstByTrabajadorIdAndSalidaIsNullOrderByEntradaDesc(yo.getId())
                .ifPresent(abierta -> {
                    throw new ConflictoException("Ya marcaste tu entrada a las "
                            + abierta.getEntrada().withZoneSameInstant(ReglaAsistencia.ZONA_DEL_LOCAL).format(HORA_Y_DIA)
                            + ": marca la salida antes de volver a entrar.");
                });

        marcacionRepository.save(Marcacion.builder()
                .trabajador(yo)
                .entrada(ahora())
                .createdBy(UsuarioActual.username())
                .build());
        return mia();
    }

    @Override
    @Transactional
    public MiAsistenciaDto marcarSalida() {
        Trabajador yo = yo();
        Marcacion abierta = marcacionRepository.findFirstByTrabajadorIdAndSalidaIsNullOrderByEntradaDesc(yo.getId())
                .orElseThrow(() -> new ConflictoException("No tienes una entrada abierta que cerrar."));
        abierta.setSalida(ahora());
        abierta.setModifiedBy(UsuarioActual.username());
        marcacionRepository.save(abierta);
        return mia();
    }

    @Override
    @Transactional(readOnly = true)
    public MiAsistenciaDto mia() {
        Trabajador yo = yo();
        LocalDate hoy = ahora().toLocalDate();
        Optional<Marcacion> abierta = marcacionRepository.findFirstByTrabajadorIdAndSalidaIsNullOrderByEntradaDesc(yo.getId());
        return MiAsistenciaDto.builder()
                .dentro(abierta.isPresent())
                .entrada(abierta.map(Marcacion::getEntrada).orElse(null))
                .turnos(turnoRepository.findByTrabajadorIdAndFechaBetweenOrderByFechaAscInicioAsc(yo.getId(), hoy,
                                hoy.plusDays(6))
                        .stream()
                        .map(TurnoDto::fromEntity)
                        .toList())
                .build();
    }

    /**
     * Una fila por turno del dia, con la marcacion que le corresponde, y una
     * mas por cada entrada que no cayo en ningun turno: quien vino a cubrir a
     * alguien tambien vino.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AsistenciaDelDiaDto> delDia(LocalDate dia) {
        ZonedDateTime ahora = ahora();
        LocalDate elegido = dia != null ? dia : ahora.toLocalDate();
        ZonedDateTime desde = elegido.atStartOfDay(ReglaAsistencia.ZONA_DEL_LOCAL);

        List<Turno> turnos = turnoRepository.findByFechaBetweenOrderByFechaAscInicioAsc(elegido, elegido);
        // Desde dos horas antes: quien entra temprano para el primer turno tambien es de este dia.
        List<Marcacion> marcaciones = marcacionRepository.findByEntradaBetweenOrderByEntradaAsc(
                desde.minusMinutes(ReglaAsistencia.MINUTOS_ANTES_QUE_CUENTA), desde.plusDays(1));

        Set<UUID> usadas = new HashSet<>();
        List<AsistenciaDelDiaDto> filas = new ArrayList<>();
        for (Turno turno : turnos) {
            ReglaAsistencia.Tramo tramoTurno = ReglaAsistencia.turno(turno.getFecha(), turno.getInicio(), turno.getFin());
            List<Marcacion> suyas = marcaciones.stream()
                    .filter(m -> m.getTrabajador().getId().equals(turno.getTrabajador().getId()))
                    .filter(m -> !usadas.contains(m.getId()))
                    .toList();
            Marcacion marcacion = suyas.stream()
                    .filter(m -> ReglaAsistencia.marcacionDelTurno(tramoTurno, List.of(tramo(m))).isPresent())
                    .findFirst()
                    .orElse(null);
            if (marcacion != null) {
                usadas.add(marcacion.getId());
            }
            ReglaAsistencia.Tramo tramoMarcacion = marcacion != null ? tramo(marcacion) : null;
            filas.add(fila(turno.getTrabajador(), tramoTurno, marcacion,
                    ReglaAsistencia.estado(tramoTurno, tramoMarcacion, ahora),
                    tramoMarcacion != null ? ReglaAsistencia.minutosTarde(tramoTurno, tramoMarcacion) : 0));
        }

        for (Marcacion m : marcaciones) {
            if (!usadas.contains(m.getId()) && !m.getEntrada().isBefore(desde)) {
                filas.add(fila(m.getTrabajador(), null, m, ReglaAsistencia.estado(null, tramo(m), ahora), 0));
            }
        }

        filas.sort(Comparator.comparing((AsistenciaDelDiaDto f) -> f.getTurnoInicio() != null ? f.getTurnoInicio() : f.getEntrada())
                .thenComparing(AsistenciaDelDiaDto::getNombre));
        return filas;
    }

    private AsistenciaDelDiaDto fila(Trabajador t, ReglaAsistencia.Tramo turno, Marcacion marcacion,
            com.chaquena.backend_logistica.asistencia.domain.EstadoAsistenciaEnum estado, long minutosTarde) {
        return AsistenciaDelDiaDto.builder()
                .trabajadorId(t.getId())
                .nombre((t.getNombres() + " " + t.getApellidos()).trim())
                .cargo(t.getCargo() != null ? t.getCargo().getNombre() : null)
                .turnoInicio(turno != null ? turno.inicio() : null)
                .turnoFin(turno != null ? turno.fin() : null)
                .entrada(marcacion != null ? marcacion.getEntrada() : null)
                .salida(marcacion != null ? marcacion.getSalida() : null)
                .estado(estado)
                .minutosTarde(minutosTarde)
                .build();
    }

    private ReglaAsistencia.Tramo tramo(Marcacion m) {
        return new ReglaAsistencia.Tramo(m.getEntrada(), m.getSalida());
    }

    private Trabajador yo() {
        return trabajadorContexto.actual()
                .orElseThrow(() -> new ConflictoException(
                        "Tu sesion no corresponde a ningun trabajador: no hay a quien marcarle la asistencia."));
    }

    private ZonedDateTime ahora() {
        return ZonedDateTime.now(ReglaAsistencia.ZONA_DEL_LOCAL);
    }
}
