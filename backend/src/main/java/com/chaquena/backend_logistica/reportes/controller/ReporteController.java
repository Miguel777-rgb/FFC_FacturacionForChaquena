package com.chaquena.backend_logistica.reportes.controller;

import com.chaquena.backend_logistica.reportes.dto.GranularidadEnum;
import com.chaquena.backend_logistica.reportes.dto.ProductoTopDto;
import com.chaquena.backend_logistica.reportes.dto.ReporteVentasDto;
import com.chaquena.backend_logistica.reportes.dto.SerieVentasDto;
import com.chaquena.backend_logistica.reportes.dto.TableroDto;
import com.chaquena.backend_logistica.reportes.dto.VentasPorMozoDto;
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

/**
 * Los indicadores del local.
 *
 * <p>Todos los endpoints aceptan {@code desde} y {@code hasta} y ninguno los
 * exige: sin ellos se responde por los ultimos treinta dias, salvo el tablero,
 * que por defecto habla del dia en curso porque es lo que se mira al abrir.
 *
 * <p>El rango se interpreta en la zona horaria con la que llega: si el
 * navegador manda {@code 2026-09-07T00:00:00-05:00}, el dia empieza a
 * medianoche en Lima y no a medianoche en Greenwich.
 */
@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Tablero, ventas, series y ranking de personal")
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

    @GetMapping("/tablero")
    @PreAuthorize("hasAnyRole('ADMIN','CAJA')")
    @Operation(operationId = "tableroLocal",
            summary = "El local de un vistazo: venta, cocina, salon, caja y eventos",
            description = "Sin rango responde por el dia en curso. Las cifras del bloque "
                    + "'ahoraMismo' son una foto del presente y no dependen del rango pedido.")
    public ResponseEntity<TableroDto> tableroLocal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime hasta) {
        ZonedDateTime fin = hasta != null ? hasta : ZonedDateTime.now();
        ZonedDateTime inicio = desde != null ? desde : fin.toLocalDate().atStartOfDay(fin.getZone());
        return ResponseEntity.ok(reporteService.tablero(inicio, fin));
    }

    @GetMapping("/serie-ventas")
    @PreAuthorize("hasAnyRole('ADMIN','CAJA')")
    @Operation(operationId = "serieVentas",
            summary = "Ventas repartidas por hora o por dia, con los bloques vacios incluidos")
    public ResponseEntity<SerieVentasDto> serieVentas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime hasta,
            @RequestParam(defaultValue = "DIA") GranularidadEnum granularidad) {
        ZonedDateTime fin = hasta != null ? hasta : ZonedDateTime.now();
        ZonedDateTime inicio = desde != null ? desde
                : (granularidad == GranularidadEnum.HORA
                        ? fin.toLocalDate().atStartOfDay(fin.getZone())
                        : fin.minusDays(30));
        return ResponseEntity.ok(reporteService.serieVentas(inicio, fin, granularidad));
    }

    @GetMapping("/ventas-por-mozo")
    @PreAuthorize("hasAnyRole('ADMIN','CAJA')")
    @Operation(operationId = "ventasPorMozo", summary = "Cuanto vendio cada mozo en el rango")
    public ResponseEntity<List<VentasPorMozoDto>> ventasPorMozo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime hasta) {
        ZonedDateTime fin = hasta != null ? hasta : ZonedDateTime.now();
        ZonedDateTime inicio = desde != null ? desde : fin.minusDays(30);
        return ResponseEntity.ok(reporteService.ventasPorMozo(inicio, fin));
    }
}
