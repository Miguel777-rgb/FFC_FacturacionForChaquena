package com.chaquena.backend_logistica.mesas.service;

import com.chaquena.backend_logistica.mesas.domain.EstadoReservaEnum;
import com.chaquena.backend_logistica.mesas.dto.ReservaRequestDto;
import com.chaquena.backend_logistica.mesas.dto.ReservaResponseDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReservaService {

    /** Las reservas de un dia de Lima, por hora. Sin dia, las de hoy. */
    List<ReservaResponseDto> listarDelDia(LocalDate dia);

    ReservaResponseDto crear(ReservaRequestDto request);

    ReservaResponseDto cambiarEstado(UUID id, EstadoReservaEnum estado);
}
