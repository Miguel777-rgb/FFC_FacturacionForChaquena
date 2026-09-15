package com.chaquena.backend_logistica.inventario.service;

import com.chaquena.backend_logistica.inventario.dto.AlergenoDto;

import java.util.List;

public interface AlergenoService {

    List<AlergenoDto> listar(boolean soloActivos);

    AlergenoDto crear(AlergenoDto request);

    AlergenoDto actualizar(Integer id, AlergenoDto request);

    AlergenoDto cambiarActivo(Integer id, boolean activo);
}
