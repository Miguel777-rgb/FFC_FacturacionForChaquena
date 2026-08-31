package com.chaquena.backend_logistica.pagos.controller;

import com.chaquena.backend_logistica.pagos.dto.AlertaFraudeRequestDto;
import com.chaquena.backend_logistica.pagos.dto.ArqueoCajaDto;
import com.chaquena.backend_logistica.pagos.dto.PagoResponseDto;
import com.chaquena.backend_logistica.pagos.service.PagoService;
import com.chaquena.backend_logistica.pedidos.dto.OrdenResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/caja")
@RequiredArgsConstructor
@Tag(name = "Caja - Arqueo y fraude", description = "Cierre de caja y bandeja de alertas")
public class CajaController {

    private final PagoService pagoService;

    @PostMapping("/ordenes/{ordenId}/alerta-fraude")
    @PreAuthorize("hasAnyRole('ADMIN','CAJA','MOZO')")
    @Operation(summary = "Marcar la comanda como fraudulenta y notificar a administracion")
    public ResponseEntity<OrdenResponseDto> alertaFraude(@PathVariable UUID ordenId,
            @Valid @RequestBody AlertaFraudeRequestDto request) {
        return ResponseEntity.ok(pagoService.alertaFraude(ordenId, request));
    }

    @PostMapping("/pagos/{pagoId}/confirmar")
    @PreAuthorize("hasAnyRole('ADMIN','CAJA')")
    @Operation(summary = "Confirmar la acreditacion de un pago con billetera o tarjeta")
    public ResponseEntity<PagoResponseDto> confirmar(@PathVariable UUID pagoId) {
        return ResponseEntity.ok(pagoService.confirmar(pagoId));
    }

    @GetMapping("/arqueo")
    @PreAuthorize("hasAnyRole('ADMIN','CAJA')")
    @Operation(summary = "Cierre de caja por metodo de pago en un rango de fechas")
    public ResponseEntity<ArqueoCajaDto> arqueo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime hasta) {
        ZonedDateTime fin = hasta != null ? hasta : ZonedDateTime.now();
        ZonedDateTime inicio = desde != null ? desde : fin.toLocalDate().atStartOfDay(fin.getZone());
        return ResponseEntity.ok(pagoService.arqueo(inicio, fin));
    }

    @GetMapping("/alertas-fraude")
    @PreAuthorize("hasAnyRole('ADMIN','CAJA')")
    @Operation(summary = "Bandeja de pagos marcados como fraudulentos")
    public ResponseEntity<List<PagoResponseDto>> alertas() {
        return ResponseEntity.ok(pagoService.alertasFraude());
    }
}
