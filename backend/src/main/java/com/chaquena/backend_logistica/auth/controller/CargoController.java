package com.chaquena.backend_logistica.auth.controller;

import com.chaquena.backend_logistica.auth.dto.ActualizarCargoRequestDto;
import com.chaquena.backend_logistica.auth.dto.CargoResponseDto;
import com.chaquena.backend_logistica.auth.dto.CrearCargoRequestDto;
import com.chaquena.backend_logistica.auth.service.CargoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * El cargo es la unica pieza que el local administra de la autorizacion: agrupa
 * roles del catalogo fijo y se asigna a las personas desde
 * {@code /api/v1/trabajadores}.
 *
 * <p>Crear y modificar exigen administrador porque un cargo es un permiso con
 * otro nombre. Consultarlos no: la pantalla de personal necesita la lista para
 * decir con que cargo trabaja cada uno.
 */
@Tag(name = "Cargos", description = "Cargos del personal y los roles que llevan asociados")
@RestController
@RequestMapping("/api/v1/cargos")
@RequiredArgsConstructor
public class CargoController {

    private final CargoService cargoService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "crearCargo", summary = "Crear cargo con los roles que agrupa")
    public ResponseEntity<CargoResponseDto> crear(@Valid @RequestBody CrearCargoRequestDto request) {
        CargoResponseDto response = cargoService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(operationId = "listarCargos", summary = "Cargos definidos con sus roles")
    public ResponseEntity<List<CargoResponseDto>> listarTodos() {
        return ResponseEntity.ok(cargoService.listarTodos());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "obtenerCargo", summary = "Obtener cargo por id")
    public ResponseEntity<CargoResponseDto> obtenerPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(cargoService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "actualizarCargo",
            summary = "Cambiar el nombre y el conjunto de roles del cargo",
            description = "Quien ya inicio sesion conserva sus permisos hasta que vuelva a entrar: "
                    + "los roles viajan dentro del JWT.")
    public ResponseEntity<CargoResponseDto> actualizar(@PathVariable Integer id,
            @Valid @RequestBody ActualizarCargoRequestDto request) {
        return ResponseEntity.ok(cargoService.actualizar(id, request));
    }
}
