package com.chaquena.backend_logistica.mesas.service;

import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.dto.MesaRequestDto;
import com.chaquena.backend_logistica.mesas.dto.MesaResponseDto;
import com.chaquena.backend_logistica.mesas.dto.ReservarMesaRequestDto;

import java.util.List;
import java.util.UUID;

public interface MesaService {
    MesaResponseDto crear(MesaRequestDto request);
    List<MesaResponseDto> mapaDelSalon();
    MesaResponseDto obtenerPorId(UUID id);
    MesaResponseDto actualizar(UUID id, MesaRequestDto request);
    MesaResponseDto cambiarEstado(UUID id, EstadoMesaEnum estado);
    MesaResponseDto reservar(UUID id, ReservarMesaRequestDto request);
    MesaResponseDto liberar(UUID id);
}
