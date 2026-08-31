package com.chaquena.backend_logistica.inventario.controller;

import com.chaquena.backend_logistica.inventario.domain.TipoInsumoEnum;
import com.chaquena.backend_logistica.inventario.dto.InsumoRequestDto;
import com.chaquena.backend_logistica.inventario.dto.InsumoResponseDto;
import com.chaquena.backend_logistica.inventario.service.InsumoService;
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
@RequestMapping("/api/v1/insumos")
@RequiredArgsConstructor
@Tag(name = "Inventario - Insumos", description = "Datos maestros de insumos y alertas de stock")
public class InsumoController {

    private final InsumoService insumoService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(summary = "Crear insumo (nace con stock cero)")
    public ResponseEntity<InsumoResponseDto> crear(@Valid @RequestBody InsumoRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(insumoService.crear(request));
    }

    @GetMapping
    @Operation(summary = "Listar insumos con filtros por tipo, texto y bajo minimo")
    public ResponseEntity<PageResponseDto<InsumoResponseDto>> buscar(
            @RequestParam(required = false) TipoInsumoEnum tipo,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean bajoMinimo,
            @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(insumoService.buscar(tipo, q, bajoMinimo, pageable));
    }

    @GetMapping("/alertas")
    @Operation(summary = "Insumos en o por debajo del stock minimo")
    public ResponseEntity<List<InsumoResponseDto>> alertas() {
        return ResponseEntity.ok(insumoService.alertas());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener insumo por id")
    public ResponseEntity<InsumoResponseDto> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(insumoService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(summary = "Actualizar datos maestros del insumo (no mueve stock)")
    public ResponseEntity<InsumoResponseDto> actualizar(@PathVariable UUID id,
            @Valid @RequestBody InsumoRequestDto request) {
        return ResponseEntity.ok(insumoService.actualizar(id, request));
    }
}
