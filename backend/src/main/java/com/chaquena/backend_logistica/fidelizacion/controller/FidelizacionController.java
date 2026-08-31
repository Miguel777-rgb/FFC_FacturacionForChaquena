package com.chaquena.backend_logistica.fidelizacion.controller;

import com.chaquena.backend_logistica.fidelizacion.dto.*;
import com.chaquena.backend_logistica.fidelizacion.service.ConfiguracionService;
import com.chaquena.backend_logistica.fidelizacion.service.FidelizacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Feedback y fidelizacion", description = "Calificaciones, cupones y parametros del local")
public class FidelizacionController {

    private final FidelizacionService fidelizacionService;
    private final ConfiguracionService configuracionService;

    @PostMapping("/ordenes/{ordenId}/feedback")
    @Operation(summary = "Registrar calificacion y evaluar la regla de las N calificaciones")
    public ResponseEntity<FeedbackResponseDto> registrar(@PathVariable UUID ordenId,
            @Valid @RequestBody FeedbackRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fidelizacionService.registrarFeedback(ordenId, request));
    }

    @GetMapping("/ordenes/{ordenId}/feedback")
    @Operation(summary = "Calificacion de una comanda")
    public ResponseEntity<FeedbackResponseDto> obtener(@PathVariable UUID ordenId) {
        return ResponseEntity.ok(fidelizacionService.feedbackDeOrden(ordenId));
    }

    @GetMapping("/clientes/{clienteId}/fidelizacion")
    @Operation(summary = "Cuantas calificaciones lleva el cliente y cuantas le faltan")
    public ResponseEntity<FidelizacionDto> progreso(@PathVariable UUID clienteId) {
        return ResponseEntity.ok(fidelizacionService.progresoDelCliente(clienteId));
    }

    @GetMapping("/clientes/{clienteId}/cupones")
    @Operation(summary = "Cupones del cliente")
    public ResponseEntity<List<CuponResponseDto>> cupones(@PathVariable UUID clienteId) {
        return ResponseEntity.ok(fidelizacionService.cuponesDelCliente(clienteId));
    }

    @PostMapping("/cupones/{codigo}/canjear")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(summary = "Canjear cupon contra una comanda")
    public ResponseEntity<CuponResponseDto> canjear(@PathVariable String codigo,
            @RequestParam UUID ordenId) {
        return ResponseEntity.ok(fidelizacionService.canjear(codigo, ordenId));
    }

    @GetMapping("/reportes/satisfaccion")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Promedios de satisfaccion por criterio y periodo")
    public ResponseEntity<ReporteSatisfaccionDto> satisfaccion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime hasta) {
        ZonedDateTime fin = hasta != null ? hasta : ZonedDateTime.now();
        ZonedDateTime inicio = desde != null ? desde : fin.minusMonths(1);
        return ResponseEntity.ok(fidelizacionService.satisfaccion(inicio, fin));
    }

    @GetMapping("/configuracion")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Parametros del local: umbral N, descuento y objetivo de cocina")
    public ResponseEntity<ConfiguracionLocalDto> configuracion() {
        return ResponseEntity.ok(ConfiguracionLocalDto.fromEntity(configuracionService.obtener()));
    }

    @PutMapping("/configuracion")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar los parametros del local")
    public ResponseEntity<ConfiguracionLocalDto> actualizarConfiguracion(
            @Valid @RequestBody ConfiguracionLocalDto request) {
        return ResponseEntity.ok(ConfiguracionLocalDto.fromEntity(
                configuracionService.actualizar(request.toEntity())));
    }
}
