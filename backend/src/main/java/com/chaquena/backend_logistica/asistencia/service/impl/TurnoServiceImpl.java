package com.chaquena.backend_logistica.asistencia.service.impl;

import com.chaquena.backend_logistica.asistencia.domain.Turno;
import com.chaquena.backend_logistica.asistencia.dto.TurnoDto;
import com.chaquena.backend_logistica.asistencia.repository.TurnoRepository;
import com.chaquena.backend_logistica.asistencia.service.ReglaAsistencia;
import com.chaquena.backend_logistica.asistencia.service.TurnoService;
import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TurnoServiceImpl implements TurnoService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final TurnoRepository turnoRepository;
    private final TrabajadorRepository trabajadorRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TurnoDto> semana(LocalDate desde) {
        LocalDate lunes = desde != null
                ? desde
                : LocalDate.now(ReglaAsistencia.ZONA_DEL_LOCAL).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return turnoRepository.findByFechaBetweenOrderByFechaAscInicioAsc(lunes, lunes.plusDays(6)).stream()
                .map(TurnoDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public TurnoDto crear(TurnoDto request) {
        Trabajador trabajador = trabajadorActivo(request.getTrabajadorId());
        validarHoras(request);
        exigirSinChoque(trabajador, request, null);

        Turno turno = Turno.builder()
                .trabajador(trabajador)
                .fecha(request.getFecha())
                .inicio(request.getInicio())
                .fin(request.getFin())
                .nota(limpio(request.getNota()))
                .createdBy(UsuarioActual.username())
                .build();
        return TurnoDto.fromEntity(turnoRepository.save(turno));
    }

    @Override
    @Transactional
    public TurnoDto actualizar(UUID id, TurnoDto request) {
        Turno turno = buscar(id);
        Trabajador trabajador = trabajadorActivo(request.getTrabajadorId());
        validarHoras(request);
        exigirSinChoque(trabajador, request, id);

        turno.setTrabajador(trabajador);
        turno.setFecha(request.getFecha());
        turno.setInicio(request.getInicio());
        turno.setFin(request.getFin());
        turno.setNota(limpio(request.getNota()));
        turno.setModifiedBy(UsuarioActual.username());
        return TurnoDto.fromEntity(turnoRepository.save(turno));
    }

    /** Un turno se borra: es un plan, no un registro. Lo que paso de verdad esta en las marcaciones. */
    @Override
    @Transactional
    public void eliminar(UUID id) {
        turnoRepository.delete(buscar(id));
    }

    private void validarHoras(TurnoDto request) {
        if (request.getInicio().equals(request.getFin())) {
            throw new IllegalArgumentException("Un turno no puede empezar y terminar a la misma hora.");
        }
    }

    /**
     * Se miran tambien el dia anterior y el siguiente: el turno de la noche del
     * lunes termina el martes y choca con uno del martes temprano.
     */
    private void exigirSinChoque(Trabajador trabajador, TurnoDto request, UUID propio) {
        ReglaAsistencia.Tramo nuevo = ReglaAsistencia.turno(request.getFecha(), request.getInicio(), request.getFin());
        turnoRepository.findByTrabajadorIdAndFechaBetweenOrderByFechaAscInicioAsc(trabajador.getId(),
                        request.getFecha().minusDays(1), request.getFecha().plusDays(1))
                .stream()
                .filter(otro -> !otro.getId().equals(propio))
                .filter(otro -> ReglaAsistencia.seSolapan(nuevo,
                        ReglaAsistencia.turno(otro.getFecha(), otro.getInicio(), otro.getFin())))
                .findFirst()
                .ifPresent(otro -> {
                    throw new ConflictoException("Ese turno se pisa con el del " + otro.getFecha().format(DIA)
                            + " de " + otro.getInicio().format(HORA) + " a " + otro.getFin().format(HORA) + ".");
                });
    }

    private Trabajador trabajadorActivo(UUID id) {
        Trabajador trabajador = trabajadorRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el trabajador", id));
        if (!Boolean.TRUE.equals(trabajador.getActivo())) {
            throw new ConflictoException(trabajador.getNombres() + " esta dado de baja: no se le asignan turnos.");
        }
        return trabajador;
    }

    private Turno buscar(UUID id) {
        return turnoRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el turno", id));
    }

    private String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
