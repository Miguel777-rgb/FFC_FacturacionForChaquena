package com.chaquena.backend_logistica.reportes.controller;

import com.chaquena.backend_logistica.reportes.dto.FormatoExportacionEnum;
import com.chaquena.backend_logistica.reportes.exportacion.Rotulos;
import com.chaquena.backend_logistica.reportes.service.ExportacionService;
import com.chaquena.backend_logistica.reportes.service.ExportacionService.ArchivoExportado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Los reportes como archivo. Cada uno pide los mismos cargos que su pantalla.
 *
 * <p>El idioma del archivo sale de {@code Accept-Language}, la cabecera que el
 * frontend ya manda en cada peticion. Los rangos se leen igual que en
 * {@link ReporteController}: sin ellos, los ultimos treinta dias.
 */
@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes - Exportar", description = "Ventas, platillos, inventario y asistencia en PDF o Excel")
public class ExportacionController {

    private static final String PDF = "application/pdf";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExportacionService exportacionService;

    @GetMapping("/ventas/exportar")
    @PreAuthorize("hasAnyRole('ADMIN','CAJA')")
    @Operation(operationId = "exportarVentas",
            summary = "Ventas del rango: resumen, canal, metodo de pago, dia y mozo")
    @ApiResponse(responseCode = "200", description = "El archivo, como adjunto", content = {
            @Content(mediaType = PDF, schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = XLSX, schema = @Schema(type = "string", format = "binary"))})
    public ResponseEntity<byte[]> exportarVentas(
            @RequestParam(defaultValue = "PDF") FormatoExportacionEnum formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime hasta,
            Locale locale) {
        ZonedDateTime fin = hasta != null ? hasta : ZonedDateTime.now();
        ZonedDateTime inicio = desde != null ? desde : fin.minusDays(30);
        return adjunto(exportacionService.ventas(inicio, fin, formato, locale));
    }

    @GetMapping("/productos/exportar")
    @PreAuthorize("hasAnyRole('ADMIN','CAJA','COCINA')")
    @Operation(operationId = "exportarProductos", summary = "Todos los platillos vendidos en el rango, del mas vendido al menos")
    @ApiResponse(responseCode = "200", description = "El archivo, como adjunto", content = {
            @Content(mediaType = PDF, schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = XLSX, schema = @Schema(type = "string", format = "binary"))})
    public ResponseEntity<byte[]> exportarProductos(
            @RequestParam(defaultValue = "PDF") FormatoExportacionEnum formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime hasta,
            Locale locale) {
        ZonedDateTime fin = hasta != null ? hasta : ZonedDateTime.now();
        ZonedDateTime inicio = desde != null ? desde : fin.minusDays(30);
        return adjunto(exportacionService.productos(inicio, fin, formato, locale));
    }

    @GetMapping("/inventario/exportar")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(operationId = "exportarInventario",
            summary = "El almacen en este momento: stock, minimo, vencimientos y valor")
    @ApiResponse(responseCode = "200", description = "El archivo, como adjunto", content = {
            @Content(mediaType = PDF, schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = XLSX, schema = @Schema(type = "string", format = "binary"))})
    public ResponseEntity<byte[]> exportarInventario(
            @RequestParam(defaultValue = "PDF") FormatoExportacionEnum formato,
            Locale locale) {
        return adjunto(exportacionService.inventario(formato, locale));
    }

    @GetMapping("/asistencia/exportar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "exportarAsistencia",
            summary = "Asistencia por dia y resumen por persona",
            description = "Sin rango, los ultimos siete dias. Un rango al reves se da vuelta; hasta un ano.")
    @ApiResponse(responseCode = "200", description = "El archivo, como adjunto", content = {
            @Content(mediaType = PDF, schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = XLSX, schema = @Schema(type = "string", format = "binary"))})
    public ResponseEntity<byte[]> exportarAsistencia(
            @RequestParam(defaultValue = "PDF") FormatoExportacionEnum formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Locale locale) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now(Rotulos.ZONA_DEL_LOCAL);
        LocalDate inicio = desde != null ? desde : fin.minusDays(6);
        return adjunto(exportacionService.asistencia(inicio, fin, formato, locale));
    }

    private ResponseEntity<byte[]> adjunto(ArchivoExportado archivo) {
        return ResponseEntity.ok()
                .contentType(archivo.formato().tipoDeContenido())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(archivo.nombre(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(archivo.contenido());
    }
}
