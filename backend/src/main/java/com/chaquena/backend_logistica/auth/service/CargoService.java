package com.chaquena.backend_logistica.auth.service;

import com.chaquena.backend_logistica.auth.dto.CargoResponseDto;
import com.chaquena.backend_logistica.auth.dto.CrearCargoRequestDto;

import java.util.List;

public interface CargoService {
    CargoResponseDto crear(CrearCargoRequestDto request);

    List<CargoResponseDto> listarTodos();

    CargoResponseDto obtenerPorId(Integer id);
}