package com.chaquena.backend_logistica.mesas.service.impl;

import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.domain.EstadoReservaEnum;
import com.chaquena.backend_logistica.mesas.domain.FormaMesaEnum;
import com.chaquena.backend_logistica.mesas.domain.Mesa;
import com.chaquena.backend_logistica.mesas.domain.Reserva;
import com.chaquena.backend_logistica.mesas.dto.MesaRequestDto;
import com.chaquena.backend_logistica.mesas.dto.MesaResponseDto;
import com.chaquena.backend_logistica.mesas.dto.PlanoRequestDto;
import com.chaquena.backend_logistica.mesas.dto.ReservaRequestDto;
import com.chaquena.backend_logistica.mesas.dto.ReservarMesaRequestDto;
import com.chaquena.backend_logistica.mesas.repository.MesaRepository;
import com.chaquena.backend_logistica.mesas.repository.ReservaRepository;
import com.chaquena.backend_logistica.mesas.service.MesaService;
import com.chaquena.backend_logistica.mesas.service.ReglaPlano;
import com.chaquena.backend_logistica.mesas.service.ReglaReservas;
import com.chaquena.backend_logistica.mesas.service.ReservaService;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MesaServiceImpl implements MesaService {

    /** Una mesa nueva ocupa dos celdas por lado: cabe una fila de cuatro con pasillo. */
    private static final int LADO_MESA_NUEVA = 2;

    private final MesaRepository mesaRepository;
    private final ReservaRepository reservaRepository;
    private final ReservaService reservaService;

    /** Se da de alta en el primer hueco de su zona, para que no aparezca encima de otra. */
    @Override
    @Transactional
    public MesaResponseDto crear(MesaRequestDto request) {
        String numero = request.getNumero().trim();
        if (mesaRepository.existsByNumero(numero)) {
            throw new ConflictoException("Ya existe la mesa " + numero + ".");
        }
        String zona = limpio(request.getZona());
        ReglaPlano.Hueco hueco = ReglaPlano.primerHueco(posicionesDeZona(zona, null), LADO_MESA_NUEVA,
                LADO_MESA_NUEVA);
        Mesa mesa = Mesa.builder()
                .numero(numero)
                .zona(zona)
                .capacidad(request.getCapacidad())
                .estado(EstadoMesaEnum.LIBRE)
                .activa(request.getActiva() == null || request.getActiva())
                .columna(hueco.columna())
                .fila(hueco.fila())
                .ancho(LADO_MESA_NUEVA)
                .alto(LADO_MESA_NUEVA)
                .forma(FormaMesaEnum.CUADRADA)
                .createdBy(UsuarioActual.username())
                .build();
        return MesaResponseDto.fromEntity(mesaRepository.save(mesa));
    }

    /**
     * Mapa del salon ordenado por zona y numero: es lo que pinta el POS. Cada
     * mesa lleva su proxima reserva de hoy, y se ve reservada si esa reserva ya
     * la esta apartando. Todas las reservas salen de una sola consulta.
     */
    @Override
    @Transactional(readOnly = true)
    public List<MesaResponseDto> mapaDelSalon() {
        ZonedDateTime ahora = ahora();
        Map<UUID, Reserva> proximas = proximasDeHoy(ahora);
        return mesaRepository.findByActivaTrueOrderByZonaAscNumeroAsc().stream()
                .map(m -> MesaResponseDto.deSalon(m, proximas.get(m.getId()), ahora))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MesaResponseDto obtenerPorId(UUID id) {
        return respuesta(buscar(id));
    }

    /** Si cambia de zona, se ubica en el primer hueco de la nueva: sus celdas eran de otro plano. */
    @Override
    @Transactional
    public MesaResponseDto actualizar(UUID id, MesaRequestDto request) {
        Mesa mesa = buscar(id);
        String numero = request.getNumero().trim();
        mesaRepository.findByNumero(numero)
                .filter(otra -> !otra.getId().equals(id))
                .ifPresent(otra -> {
                    throw new ConflictoException("Ya existe otra mesa con el numero " + numero + ".");
                });

        String zona = limpio(request.getZona());
        if (!ReglaPlano.claveDeZona(mesa.getZona()).equals(ReglaPlano.claveDeZona(zona))) {
            ReglaPlano.Hueco hueco = ReglaPlano.primerHueco(posicionesDeZona(zona, id), mesa.getAncho(),
                    mesa.getAlto());
            mesa.setColumna(hueco.columna());
            mesa.setFila(hueco.fila());
        }
        mesa.setNumero(numero);
        mesa.setZona(zona);
        mesa.setCapacidad(request.getCapacidad());
        if (request.getActiva() != null) {
            mesa.setActiva(request.getActiva());
        }
        mesa.setModifiedBy(UsuarioActual.username());
        return respuesta(mesaRepository.save(mesa));
    }

    @Override
    @Transactional
    public MesaResponseDto cambiarEstado(UUID id, EstadoMesaEnum estado) {
        if (estado == EstadoMesaEnum.RESERVADA) {
            throw new IllegalArgumentException(
                    "Una mesa no se reserva cambiando su estado: se le crea una reserva.");
        }
        Mesa mesa = buscar(id);
        mesa.setEstado(estado);
        mesa.setModifiedBy(UsuarioActual.username());
        return respuesta(mesaRepository.save(mesa));
    }

    /**
     * Se mantiene para los clientes de antes: crea una reserva de la duracion
     * por defecto, para tantas personas como sillas tiene la mesa.
     */
    @Override
    @Transactional
    public MesaResponseDto reservar(UUID id, ReservarMesaRequestDto request) {
        Mesa mesa = buscar(id);
        reservaService.crear(ReservaRequestDto.builder()
                .mesaId(id)
                .nombre(request.getANombreDe())
                .personas(mesa.getCapacidad() != null ? mesa.getCapacidad() : 1)
                .inicio(request.getPara())
                .build());
        return respuesta(mesa);
    }

    /** Libera la mesa y cancela la reserva que la estaba apartando, si la habia. */
    @Override
    @Transactional
    public MesaResponseDto liberar(UUID id) {
        Mesa mesa = buscar(id);
        ZonedDateTime ahora = ahora();
        String autor = UsuarioActual.username();
        for (Reserva reserva : activasDeLaMesa(id, ahora)) {
            if (ReglaReservas.apartaLaMesa(reserva, ahora)) {
                reserva.setEstado(EstadoReservaEnum.CANCELADA);
                reserva.setModifiedBy(autor);
            }
        }
        mesa.setEstado(EstadoMesaEnum.LIBRE);
        mesa.setModifiedBy(autor);
        return respuesta(mesaRepository.save(mesa));
    }

    /**
     * Se valida el plano entero con las posiciones nuevas antes de escribir
     * nada: dos mesas que intercambian sitio son validas juntas, aunque cada
     * movimiento por separado pise a la otra.
     */
    @Override
    @Transactional
    public List<MesaResponseDto> guardarPlano(PlanoRequestDto request) {
        Map<UUID, PlanoRequestDto.PosicionMesa> cambios = new HashMap<>();
        for (PlanoRequestDto.PosicionMesa p : request.getMesas()) {
            if (cambios.put(p.getId(), p) != null) {
                throw new IllegalArgumentException("El plano trae dos veces la misma mesa.");
            }
        }

        Map<UUID, Mesa> activas = mesaRepository.findByActivaTrueOrderByZonaAscNumeroAsc().stream()
                .collect(Collectors.toMap(Mesa::getId, Function.identity()));
        for (UUID id : cambios.keySet()) {
            if (!activas.containsKey(id)) {
                throw RecursoNoEncontradoException.de("la mesa", id);
            }
        }

        List<ReglaPlano.Posicion> plano = activas.values().stream()
                .map(m -> {
                    PlanoRequestDto.PosicionMesa p = cambios.get(m.getId());
                    return p == null
                            ? posicion(m)
                            : new ReglaPlano.Posicion(m.getNumero(), m.getZona(), p.getColumna(), p.getFila(),
                                    p.getAncho(), p.getAlto());
                })
                .toList();
        ReglaPlano.validar(plano);

        String autor = UsuarioActual.username();
        for (PlanoRequestDto.PosicionMesa p : cambios.values()) {
            Mesa mesa = activas.get(p.getId());
            mesa.setColumna(p.getColumna());
            mesa.setFila(p.getFila());
            mesa.setAncho(p.getAncho());
            mesa.setAlto(p.getAlto());
            if (p.getForma() != null) {
                mesa.setForma(p.getForma());
            }
            mesa.setModifiedBy(autor);
        }
        mesaRepository.saveAll(activas.values());
        return mapaDelSalon();
    }

    private MesaResponseDto respuesta(Mesa mesa) {
        ZonedDateTime ahora = ahora();
        Reserva proxima = activasDeLaMesa(mesa.getId(), ahora).stream()
                .min(Comparator.comparing(Reserva::getInicio))
                .orElse(null);
        return MesaResponseDto.deSalon(mesa, proxima, ahora);
    }

    /** Las activas que aun no terminaron y empiezan antes de que acabe el dia de Lima. */
    private Map<UUID, Reserva> proximasDeHoy(ZonedDateTime ahora) {
        Map<UUID, Reserva> proximas = new HashMap<>();
        for (Reserva r : reservaRepository.findByEstadoInAndInicioBetween(ReglaReservas.ACTIVAS,
                ahora.minusMinutes(ReglaReservas.DURACION_MAXIMA), finDelDia(ahora))) {
            if (ReglaReservas.fin(r).isAfter(ahora)) {
                proximas.merge(r.getMesa().getId(), r, (a, b) -> a.getInicio().isBefore(b.getInicio()) ? a : b);
            }
        }
        return proximas;
    }

    private List<Reserva> activasDeLaMesa(UUID mesaId, ZonedDateTime ahora) {
        return reservaRepository.findByMesaIdAndEstadoInAndInicioBetween(mesaId, ReglaReservas.ACTIVAS,
                        ahora.minusMinutes(ReglaReservas.DURACION_MAXIMA), finDelDia(ahora))
                .stream()
                .filter(r -> ReglaReservas.fin(r).isAfter(ahora))
                .toList();
    }

    private List<ReglaPlano.Posicion> posicionesDeZona(String zona, UUID excluida) {
        String clave = ReglaPlano.claveDeZona(zona);
        return mesaRepository.findByActivaTrueOrderByZonaAscNumeroAsc().stream()
                .filter(m -> !m.getId().equals(excluida))
                .filter(m -> ReglaPlano.claveDeZona(m.getZona()).equals(clave))
                .map(this::posicion)
                .toList();
    }

    private ReglaPlano.Posicion posicion(Mesa m) {
        return new ReglaPlano.Posicion(m.getNumero(), m.getZona(), m.getColumna(), m.getFila(), m.getAncho(),
                m.getAlto());
    }

    private ZonedDateTime ahora() {
        return ZonedDateTime.now(ReglaReservas.ZONA_DEL_LOCAL);
    }

    private ZonedDateTime finDelDia(ZonedDateTime ahora) {
        return ahora.toLocalDate().plusDays(1).atStartOfDay(ReglaReservas.ZONA_DEL_LOCAL);
    }

    private Mesa buscar(UUID id) {
        return mesaRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la mesa", id));
    }

    private String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
