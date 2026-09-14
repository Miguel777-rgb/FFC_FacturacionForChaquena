package com.chaquena.backend_logistica.local.controller;

import com.chaquena.backend_logistica.local.dto.DatosLocalDto;
import com.chaquena.backend_logistica.local.service.DatosLocalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Leer los datos del local es de cualquier sesion: la caja los necesita para
 * el encabezado del ticket, y no hay nada en ellos que no este ya en la puerta
 * del local. Cambiarlos es del administrador.
 */
@RestController
@RequestMapping("/api/v1/local")
@RequiredArgsConstructor
@Tag(name = "Local", description = "Datos del local, horario e IGV")
public class LocalController {

    private final DatosLocalService datosLocalService;

    @GetMapping
    @Operation(operationId = "obtenerDatosLocal", summary = "Datos del local, su horario y el IGV")
    public ResponseEntity<DatosLocalDto> obtener() {
        return ResponseEntity.ok(datosLocalService.obtener());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "actualizarDatosLocal", summary = "Actualizar los datos del local, su horario y el IGV")
    public ResponseEntity<DatosLocalDto> actualizar(@Valid @RequestBody DatosLocalDto request) {
        return ResponseEntity.ok(datosLocalService.actualizar(request));
    }
}
