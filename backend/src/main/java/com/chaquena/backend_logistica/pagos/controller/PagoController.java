package com.chaquena.backend_logistica.pagos.controller;

import com.chaquena.backend_logistica.pagos.dto.AlertaFraudeRequestDto;
import com.chaquena.backend_logistica.pagos.dto.PagoResponseDto;
import com.chaquena.backend_logistica.pagos.dto.RegistrarPagoRequestDto;
import com.chaquena.backend_logistica.pagos.service.PagoService;
import com.chaquena.backend_logistica.pedidos.dto.OrdenResponseDto;
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
@RequestMapping("/api/v1/ordenes/{ordenId}/pagos")
@RequiredArgsConstructor
@Tag(name = "Caja - Pagos", description = "Cobros de la comanda y alerta de fraude")
public class PagoController {

    private final PagoService pagoService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CAJA','MOZO')")
    @Operation(summary = "Registrar cobro (efectivo con vuelto, billetera o tarjeta con referencia)")
    public ResponseEntity<PagoResponseDto> registrar(@PathVariable UUID ordenId,
            @Valid @RequestBody RegistrarPagoRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagoService.registrar(ordenId, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CAJA','MOZO')")
    @Operation(summary = "Pagos de la comanda, incluidos los intentos pendientes")
    public ResponseEntity<List<PagoResponseDto>> listar(@PathVariable UUID ordenId) {
        return ResponseEntity.ok(pagoService.pagosDeOrden(ordenId));
    }
}
