package com.chaquena.backend_logistica.inventario.service;

import com.chaquena.backend_logistica.inventario.domain.TipoInsumoEnum;
import com.chaquena.backend_logistica.inventario.dto.InsumoRequestDto;
import com.chaquena.backend_logistica.inventario.dto.InsumoResponseDto;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface InsumoService {
    InsumoResponseDto crear(InsumoRequestDto request);
    PageResponseDto<InsumoResponseDto> buscar(TipoInsumoEnum tipo, String termino, boolean bajoMinimo, Pageable pageable);
    InsumoResponseDto obtenerPorId(UUID id);
    InsumoResponseDto actualizar(UUID id, InsumoRequestDto request);
    List<InsumoResponseDto> alertas();
}
