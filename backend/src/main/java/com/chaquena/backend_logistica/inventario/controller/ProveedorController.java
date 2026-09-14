package com.chaquena.backend_logistica.inventario.controller;

import com.chaquena.backend_logistica.inventario.dto.ProveedorDto;
import com.chaquena.backend_logistica.inventario.service.ProveedorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/proveedores")
@RequiredArgsConstructor
@Tag(name = "Inventario - Proveedores", description = "A quien se le compra cada insumo")
public class ProveedorController {

    private final ProveedorService proveedorService;

    @GetMapping
    @Operation(operationId = "listarProveedores", summary = "Proveedores por nombre; con soloActivos, los que se pueden elegir en una compra")
    public ResponseEntity<List<ProveedorDto>> listar(@RequestParam(defaultValue = "false") boolean soloActivos) {
        return ResponseEntity.ok(proveedorService.listar(soloActivos));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(operationId = "crearProveedor", summary = "Dar de alta un proveedor")
    public ResponseEntity<ProveedorDto> crear(@Valid @RequestBody ProveedorDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(proveedorService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(operationId = "actualizarProveedor", summary = "Actualizar los datos de un proveedor")
    public ResponseEntity<ProveedorDto> actualizar(@PathVariable UUID id, @Valid @RequestBody ProveedorDto request) {
        return ResponseEntity.ok(proveedorService.actualizar(id, request));
    }

    @PatchMapping("/{id}/activo")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(operationId = "cambiarActivoProveedor", summary = "Dar de baja o reactivar un proveedor")
    public ResponseEntity<ProveedorDto> cambiarActivo(@PathVariable UUID id, @RequestParam boolean activo) {
        return ResponseEntity.ok(proveedorService.cambiarActivo(id, activo));
    }
}
