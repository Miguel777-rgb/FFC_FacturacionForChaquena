package com.chaquena.backend_logistica.inventario.service;

import com.chaquena.backend_logistica.inventario.dto.ProveedorDto;

import java.util.List;
import java.util.UUID;

public interface ProveedorService {

    /** Por nombre. Con {@code soloActivos}, los que se pueden elegir en una compra. */
    List<ProveedorDto> listar(boolean soloActivos);

    ProveedorDto crear(ProveedorDto request);

    ProveedorDto actualizar(UUID id, ProveedorDto request);

    ProveedorDto cambiarActivo(UUID id, boolean activo);
}
