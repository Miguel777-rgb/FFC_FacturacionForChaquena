package com.chaquena.backend_logistica.auth.service;

import com.chaquena.backend_logistica.auth.dto.ActualizarTrabajadorRequestDto;
import com.chaquena.backend_logistica.auth.dto.BootstrapAdminRequestDto;
import com.chaquena.backend_logistica.auth.dto.RegistrarTrabajadorRequestDto;
import com.chaquena.backend_logistica.auth.dto.TrabajadorResponseDto;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TrabajadorService {

    TrabajadorResponseDto registrar(RegistrarTrabajadorRequestDto request);

    /** Alta del primer administrador; solo con la tabla vacia. */
    TrabajadorResponseDto bootstrapPrimerAdmin(BootstrapAdminRequestDto request);

    PageResponseDto<TrabajadorResponseDto> listar(Integer cargoId, Pageable pageable);

    List<TrabajadorResponseDto> activos();

    TrabajadorResponseDto obtenerPorId(UUID id);

    TrabajadorResponseDto actualizar(UUID id, ActualizarTrabajadorRequestDto request);

    TrabajadorResponseDto cambiarActivo(UUID id, boolean activo);
}
