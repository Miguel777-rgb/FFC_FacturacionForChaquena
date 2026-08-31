package com.chaquena.backend_logistica.inventario.service;

import com.chaquena.backend_logistica.inventario.dto.PromocionRequestDto;
import com.chaquena.backend_logistica.inventario.dto.PromocionResponseDto;

import java.util.List;
import java.util.UUID;

public interface PromocionService {
    PromocionResponseDto crear(PromocionRequestDto request);
    List<PromocionResponseDto> listar(boolean soloVigentes);
    PromocionResponseDto obtenerPorId(UUID id);
    PromocionResponseDto actualizar(UUID id, PromocionRequestDto request);
    PromocionResponseDto cambiarActiva(UUID id, boolean activa);

    /** Promociones vigentes con la disponibilidad del insumo extra ya resuelta. */
    List<PromocionResponseDto> aplicables();
}
