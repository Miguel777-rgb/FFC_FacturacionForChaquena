package com.chaquena.backend_logistica.inventario.controller;

import com.chaquena.backend_logistica.inventario.domain.TipoComplementoEnum;
import com.chaquena.backend_logistica.inventario.dto.ComplementoRequestDto;
import com.chaquena.backend_logistica.inventario.dto.ComplementoResponseDto;
import com.chaquena.backend_logistica.inventario.service.ComplementoService;
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
@RequestMapping("/api/v1/complementos")
@RequiredArgsConstructor
@Tag(name = "Catalogo - Complementos", description = "Bebidas, helados, salsas y extras")
public class ComplementoController {

    private final ComplementoService complementoService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(summary = "Crear complemento")
    public ResponseEntity<ComplementoResponseDto> crear(@Valid @RequestBody ComplementoRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complementoService.crear(request));
    }

    @GetMapping
    @Operation(summary = "Listar complementos, opcionalmente por tipo")
    public ResponseEntity<List<ComplementoResponseDto>> listar(
            @RequestParam(required = false) TipoComplementoEnum tipo,
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        return ResponseEntity.ok(complementoService.listar(tipo, soloActivos));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener complemento por id")
    public ResponseEntity<ComplementoResponseDto> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(complementoService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(summary = "Actualizar complemento")
    public ResponseEntity<ComplementoResponseDto> actualizar(@PathVariable UUID id,
            @Valid @RequestBody ComplementoRequestDto request) {
        return ResponseEntity.ok(complementoService.actualizar(id, request));
    }

    @PatchMapping("/{id}/activo")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN','COCINA')")
    @Operation(summary = "Habilitar o deshabilitar complemento")
    public ResponseEntity<ComplementoResponseDto> cambiarActivo(@PathVariable UUID id,
            @RequestParam boolean activo) {
        return ResponseEntity.ok(complementoService.cambiarActivo(id, activo));
    }
}
