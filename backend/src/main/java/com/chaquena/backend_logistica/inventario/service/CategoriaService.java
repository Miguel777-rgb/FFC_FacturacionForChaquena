package com.chaquena.backend_logistica.inventario.service;

import com.chaquena.backend_logistica.inventario.dto.CategoriaRequestDto;
import com.chaquena.backend_logistica.inventario.dto.CategoriaResponseDto;

import java.util.List;

public interface CategoriaService {
    CategoriaResponseDto crear(CategoriaRequestDto request);
    List<CategoriaResponseDto> listarTodas();
    CategoriaResponseDto obtenerPorId(Integer id);
    CategoriaResponseDto actualizar(Integer id, CategoriaRequestDto request);
    void eliminar(Integer id);
}
