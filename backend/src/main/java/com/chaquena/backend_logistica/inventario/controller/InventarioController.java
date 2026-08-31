package com.chaquena.backend_logistica.inventario.controller;

import com.chaquena.backend_logistica.inventario.dto.*;
import com.chaquena.backend_logistica.inventario.service.InventarioService;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventario")
@RequiredArgsConstructor
@Tag(name = "Inventario - Movimientos", description = "Kardex, mermas, cocinado y conteo fisico")
public class InventarioController {

    private final InventarioService inventarioService;

    @PostMapping("/movimientos")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN','COCINA')")
    @Operation(summary = "Registrar entrada por compra o merma")
    public ResponseEntity<MovimientoResponseDto> registrar(
            @Valid @RequestBody MovimientoRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventarioService.registrarMovimiento(request));
    }

    @PostMapping("/transformaciones")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN','COCINA')")
    @Operation(summary = "Cocinar: consume insumo crudo y acredita el cocido resultante")
    public ResponseEntity<List<MovimientoResponseDto>> transformar(
            @Valid @RequestBody TransformacionRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventarioService.transformar(request));
    }

    @PostMapping("/conteo-fisico")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(summary = "Conteo de cierre: ajusta el sistema a lo contado en almacen")
    public ResponseEntity<ConteoFisicoResponseDto> conteoFisico(
            @Valid @RequestBody ConteoFisicoRequestDto request) {
        return ResponseEntity.ok(inventarioService.conteoFisico(request));
    }

    @GetMapping("/kardex/{insumoId}")
    @Operation(summary = "Historial de movimientos de un insumo")
    public ResponseEntity<PageResponseDto<MovimientoResponseDto>> kardex(@PathVariable UUID insumoId,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(inventarioService.kardex(insumoId, pageable));
    }

    @PostMapping("/disponibilidad")
    @Operation(summary = "Verifica si un carrito propuesto se puede preparar con el stock actual")
    public ResponseEntity<DisponibilidadResponseDto> disponibilidad(
            @Valid @RequestBody DisponibilidadRequestDto request) {
        return ResponseEntity.ok(inventarioService.verificarDisponibilidad(request));
    }

    @GetMapping("/resumen")
    @Operation(summary = "Indicadores de inventario para el panel")
    public ResponseEntity<ResumenInventarioDto> resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime hasta) {
        ZonedDateTime fin = hasta != null ? hasta : ZonedDateTime.now();
        ZonedDateTime inicio = desde != null ? desde : fin.minusDays(1);
        return ResponseEntity.ok(inventarioService.resumen(inicio, fin));
    }
}
