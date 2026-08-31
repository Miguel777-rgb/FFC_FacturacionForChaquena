package com.chaquena.backend_logistica.inventario.service;

import com.chaquena.backend_logistica.inventario.domain.TipoComplementoEnum;
import com.chaquena.backend_logistica.inventario.dto.ComplementoRequestDto;
import com.chaquena.backend_logistica.inventario.dto.ComplementoResponseDto;

import java.util.List;
import java.util.UUID;

public interface ComplementoService {
    ComplementoResponseDto crear(ComplementoRequestDto request);
    List<ComplementoResponseDto> listar(TipoComplementoEnum tipo, boolean soloActivos);
    ComplementoResponseDto obtenerPorId(UUID id);
    ComplementoResponseDto actualizar(UUID id, ComplementoRequestDto request);
    ComplementoResponseDto cambiarActivo(UUID id, boolean activo);
}
