package com.chaquena.backend_logistica.inventario.controller;

import com.chaquena.backend_logistica.inventario.dto.PromocionRequestDto;
import com.chaquena.backend_logistica.inventario.dto.PromocionResponseDto;
import com.chaquena.backend_logistica.inventario.service.PromocionService;
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
@RequestMapping("/api/v1/promociones")
@RequiredArgsConstructor
@Tag(name = "Catalogo - Promociones", description = "Descuentos y promociones con insumo extra")
public class PromocionController {

    private final PromocionService promocionService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear promocion")
    public ResponseEntity<PromocionResponseDto> crear(@Valid @RequestBody PromocionRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promocionService.crear(request));
    }

    @GetMapping
    @Operation(summary = "Listar promociones; por defecto solo las vigentes por fecha")
    public ResponseEntity<List<PromocionResponseDto>> listar(
            @RequestParam(defaultValue = "true") boolean soloVigentes) {
        return ResponseEntity.ok(promocionService.listar(soloVigentes));
    }

    @GetMapping("/aplicables")
    @Operation(summary = "Promociones vigentes con el stock del insumo extra ya validado")
    public ResponseEntity<List<PromocionResponseDto>> aplicables() {
        return ResponseEntity.ok(promocionService.aplicables());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener promocion por id")
    public ResponseEntity<PromocionResponseDto> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(promocionService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar promocion")
    public ResponseEntity<PromocionResponseDto> actualizar(@PathVariable UUID id,
            @Valid @RequestBody PromocionRequestDto request) {
        return ResponseEntity.ok(promocionService.actualizar(id, request));
    }

    @PatchMapping("/{id}/activa")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Encender o apagar la promocion")
    public ResponseEntity<PromocionResponseDto> cambiarActiva(@PathVariable UUID id,
            @RequestParam boolean activa) {
        return ResponseEntity.ok(promocionService.cambiarActiva(id, activa));
    }
}
