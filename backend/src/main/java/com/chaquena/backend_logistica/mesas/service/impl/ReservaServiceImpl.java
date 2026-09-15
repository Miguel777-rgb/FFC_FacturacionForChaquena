package com.chaquena.backend_logistica.mesas.service.impl;

import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.domain.EstadoReservaEnum;
import com.chaquena.backend_logistica.mesas.domain.Mesa;
import com.chaquena.backend_logistica.mesas.domain.Reserva;
import com.chaquena.backend_logistica.mesas.dto.ReservaRequestDto;
import com.chaquena.backend_logistica.mesas.dto.ReservaResponseDto;
import com.chaquena.backend_logistica.mesas.repository.MesaRepository;
import com.chaquena.backend_logistica.mesas.repository.ReservaRepository;
import com.chaquena.backend_logistica.mesas.service.ReglaReservas;
import com.chaquena.backend_logistica.mesas.service.ReservaService;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservaServiceImpl implements ReservaService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Unos minutos de gracia: quien reserva "para ahora" no esta reservando en el pasado. */
    private static final int MINUTOS_DE_GRACIA = 15;

    private final ReservaRepository reservaRepository;
    private final MesaRepository mesaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponseDto> listarDelDia(LocalDate dia) {
        LocalDate elegido = dia != null ? dia : LocalDate.now(ReglaReservas.ZONA_DEL_LOCAL);
        ZonedDateTime desde = elegido.atStartOfDay(ReglaReservas.ZONA_DEL_LOCAL);
        return reservaRepository.findByInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(desde, desde.plusDays(1))
                .stream()
                .map(ReservaResponseDto::fromEntity)
                .toList();
    }

    /**
     * Antes de guardar se mira todo lo que haria la reserva imposible de
     * cumplir: una mesa que no se puede usar, mas personas que sillas, una hora
     * que ya paso o una mesa que ya esta apartada a esa hora.
     */
    @Override
    @Transactional
    public ReservaResponseDto crear(ReservaRequestDto request) {
        Mesa mesa = mesaRepository.findById(request.getMesaId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("la mesa", request.getMesaId()));
        if (!Boolean.TRUE.equals(mesa.getActiva()) || mesa.getEstado() == EstadoMesaEnum.INHABILITADA) {
            throw new ConflictoException("La mesa " + mesa.getNumero() + " esta inhabilitada: no se puede reservar.");
        }
        if (mesa.getCapacidad() != null && request.getPersonas() > mesa.getCapacidad()) {
            throw new IllegalArgumentException("La mesa " + mesa.getNumero() + " es para " + mesa.getCapacidad()
                    + " personas y la reserva es para " + request.getPersonas() + ".");
        }
        ZonedDateTime ahora = ZonedDateTime.now(ReglaReservas.ZONA_DEL_LOCAL);
        if (request.getInicio().isBefore(ahora.minusMinutes(MINUTOS_DE_GRACIA))) {
            throw new IllegalArgumentException("Una reserva no puede empezar en el pasado.");
        }

        int duracion = request.getDuracionMinutos() != null
                ? request.getDuracionMinutos()
                : ReglaReservas.DURACION_POR_DEFECTO;
        exigirMesaSinChoque(mesa, request.getInicio(), duracion);

        Reserva reserva = Reserva.builder()
                .mesa(mesa)
                .nombre(request.getNombre().trim())
                .celular(limpio(request.getCelular()))
                .personas(request.getPersonas())
                .inicio(request.getInicio())
                .duracionMinutos(duracion)
                .estado(EstadoReservaEnum.PENDIENTE)
                .nota(limpio(request.getNota()))
                .createdBy(UsuarioActual.username())
                .build();
        return ReservaResponseDto.fromEntity(reservaRepository.save(reserva));
    }

    @Override
    @Transactional
    public ReservaResponseDto cambiarEstado(UUID id, EstadoReservaEnum estado) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la reserva", id));
        if (!ReglaReservas.transiciones(reserva.getEstado()).contains(estado)) {
            throw new ConflictoException("Una reserva " + reserva.getEstado() + " no puede pasar a " + estado + ".");
        }
        reserva.setEstado(estado);
        reserva.setModifiedBy(UsuarioActual.username());
        return ReservaResponseDto.fromEntity(reservaRepository.save(reserva));
    }

    private void exigirMesaSinChoque(Mesa mesa, ZonedDateTime inicio, int duracion) {
        reservaRepository.findByMesaIdAndEstadoInAndInicioBetween(mesa.getId(), ReglaReservas.ACTIVAS,
                        inicio.minusMinutes(ReglaReservas.DURACION_MAXIMA), inicio.plusMinutes(duracion))
                .stream()
                .filter(otra -> ReglaReservas.seSolapan(inicio, duracion, otra.getInicio(), otra.getDuracionMinutos()))
                .findFirst()
                .ifPresent(otra -> {
                    throw new ConflictoException("La mesa " + mesa.getNumero() + " ya esta reservada de "
                            + hora(otra.getInicio()) + " a " + hora(ReglaReservas.fin(otra))
                            + " a nombre de " + otra.getNombre() + ".");
                });
    }

    private String hora(ZonedDateTime instante) {
        return instante.withZoneSameInstant(ReglaReservas.ZONA_DEL_LOCAL).format(HORA);
    }

    private String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
