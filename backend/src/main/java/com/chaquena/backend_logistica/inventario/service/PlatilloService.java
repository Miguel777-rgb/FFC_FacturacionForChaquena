package com.chaquena.backend_logistica.inventario.service;

import com.chaquena.backend_logistica.inventario.dto.*;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PlatilloService {
    PlatilloResponseDto crear(PlatilloRequestDto request);
    PageResponseDto<PlatilloResponseDto> buscar(Integer categoriaId, Boolean activo, String termino, Pageable pageable);
    PlatilloResponseDto obtenerPorId(UUID id);
    PlatilloResponseDto actualizar(UUID id, PlatilloRequestDto request);
    PlatilloResponseDto cambiarActivo(UUID id, boolean activo);
    List<RecetaItemDto> obtenerReceta(UUID id);
    List<RecetaItemDto> reemplazarReceta(UUID id, RecetaRequestDto request);
    List<PlatilloDisponibleDto> menuDisponible();
}
