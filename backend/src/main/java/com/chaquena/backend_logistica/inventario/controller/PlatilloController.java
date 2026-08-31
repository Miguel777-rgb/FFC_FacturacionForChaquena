package com.chaquena.backend_logistica.inventario.controller;

import com.chaquena.backend_logistica.inventario.dto.*;
import com.chaquena.backend_logistica.inventario.service.PlatilloService;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platillos")
@RequiredArgsConstructor
@Tag(name = "Catalogo - Platillos", description = "Carta, precios y recetas (BOM)")
public class PlatilloController {

    private final PlatilloService platilloService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(summary = "Crear platillo")
    public ResponseEntity<PlatilloResponseDto> crear(@Valid @RequestBody PlatilloRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platilloService.crear(request));
    }

    @GetMapping
    @Operation(summary = "Listar platillos con filtros y paginacion")
    public ResponseEntity<PageResponseDto<PlatilloResponseDto>> buscar(
            @RequestParam(required = false) Integer categoriaId,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(platilloService.buscar(categoriaId, activo, q, pageable));
    }

    /**
     * Carta con disponibilidad ya calculada contra el stock. La consumen el POS
     * y los bots para no ofrecer platillos que cocina no puede hacer.
     */
    @GetMapping("/menu-disponible")
    @Operation(summary = "Carta con disponibilidad calculada segun stock de insumos")
    public ResponseEntity<List<PlatilloDisponibleDto>> menuDisponible() {
        return ResponseEntity.ok(platilloService.menuDisponible());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener platillo con su receta")
    public ResponseEntity<PlatilloResponseDto> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(platilloService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(summary = "Actualizar platillo")
    public ResponseEntity<PlatilloResponseDto> actualizar(@PathVariable UUID id,
            @Valid @RequestBody PlatilloRequestDto request) {
        return ResponseEntity.ok(platilloService.actualizar(id, request));
    }

    @PatchMapping("/{id}/activo")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN','COCINA')")
    @Operation(summary = "Sacar de carta o reponer sin borrar el historico de ventas")
    public ResponseEntity<PlatilloResponseDto> cambiarActivo(@PathVariable UUID id,
            @RequestParam boolean activo) {
        return ResponseEntity.ok(platilloService.cambiarActivo(id, activo));
    }

    @GetMapping("/{id}/receta")
    @Operation(summary = "Receta del platillo (lista de insumos y cantidades)")
    public ResponseEntity<List<RecetaItemDto>> obtenerReceta(@PathVariable UUID id) {
        return ResponseEntity.ok(platilloService.obtenerReceta(id));
    }

    @PutMapping("/{id}/receta")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(summary = "Reemplazar la receta completa del platillo")
    public ResponseEntity<List<RecetaItemDto>> reemplazarReceta(@PathVariable UUID id,
            @Valid @RequestBody RecetaRequestDto request) {
        return ResponseEntity.ok(platilloService.reemplazarReceta(id, request));
    }
}
