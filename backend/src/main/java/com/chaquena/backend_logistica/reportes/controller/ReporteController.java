package com.chaquena.backend_logistica.reportes.controller;

import com.chaquena.backend_logistica.reportes.dto.ProductoTopDto;
import com.chaquena.backend_logistica.reportes.dto.ReporteVentasDto;
import com.chaquena.backend_logistica.reportes.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Ventas y platillos mas vendidos")
public class ReporteController {

    private final ReporteService reporteService;

    @GetMapping("/ventas")
    @PreAuthorize("hasAnyRole('ADMIN','CAJA')")
    @Operation(summary = "Ventas por rango de fechas y canal de origen")
    public ResponseEntity<ReporteVentasDto> ventas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime hasta) {
        ZonedDateTime fin = hasta != null ? hasta : ZonedDateTime.now();
        ZonedDateTime inicio = desde != null ? desde : fin.minusDays(30);
        return ResponseEntity.ok(reporteService.ventas(inicio, fin));
    }

    @GetMapping("/productos-top")
    @PreAuthorize("hasAnyRole('ADMIN','CAJA','COCINA')")
    @Operation(summary = "Platillos mas vendidos, para decidir la carta")
    public ResponseEntity<List<ProductoTopDto>> productosTop(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime hasta,
            @RequestParam(defaultValue = "10") int limite) {
        ZonedDateTime fin = hasta != null ? hasta : ZonedDateTime.now();
        ZonedDateTime inicio = desde != null ? desde : fin.minusDays(30);
        return ResponseEntity.ok(reporteService.productosTop(inicio, fin, limite));
    }
}
