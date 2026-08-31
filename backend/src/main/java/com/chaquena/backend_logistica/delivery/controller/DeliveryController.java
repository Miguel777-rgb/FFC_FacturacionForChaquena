package com.chaquena.backend_logistica.delivery.controller;

import com.chaquena.backend_logistica.delivery.dto.AsignarDeliveryRequestDto;
import com.chaquena.backend_logistica.delivery.dto.DeliveryInfoDto;
import com.chaquena.backend_logistica.delivery.dto.VerificarOtpRequestDto;
import com.chaquena.backend_logistica.delivery.service.DeliveryService;
import com.chaquena.backend_logistica.shared.dto.MensajeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Despacho - Reparto", description = "Asignacion, salida a ruta y entrega con OTP")
public class DeliveryController {

    private final DeliveryService deliveryService;

    @PostMapping("/ordenes/{ordenId}/delivery/asignar")
    @PreAuthorize("hasAnyRole('ADMIN','DELIVERY','MOZO')")
    @Operation(summary = "Asignar transportista y vehiculo, y fijar el tiempo estimado")
    public ResponseEntity<DeliveryInfoDto> asignar(@PathVariable UUID ordenId,
            @Valid @RequestBody AsignarDeliveryRequestDto request) {
        return ResponseEntity.ok(deliveryService.asignar(ordenId, request));
    }

    @PostMapping("/ordenes/{ordenId}/delivery/despachar")
    @PreAuthorize("hasAnyRole('ADMIN','DELIVERY','MOZO')")
    @Operation(summary = "Sale a ruta: sella la hora y avisa al cliente por el bot")
    public ResponseEntity<DeliveryInfoDto> despachar(@PathVariable UUID ordenId) {
        return ResponseEntity.ok(deliveryService.despachar(ordenId));
    }

    @PostMapping("/ordenes/{ordenId}/delivery/otp/reenviar")
    @PreAuthorize("hasAnyRole('ADMIN','DELIVERY','MOZO')")
    @Operation(summary = "Reenviar el codigo de entrega al cliente")
    public ResponseEntity<MensajeDto> reenviarOtp(@PathVariable UUID ordenId) {
        return ResponseEntity.ok(MensajeDto.de(deliveryService.reenviarOtp(ordenId)));
    }

    @PostMapping("/ordenes/{ordenId}/delivery/otp/verificar")
    @PreAuthorize("hasAnyRole('ADMIN','DELIVERY','MOZO')")
    @Operation(summary = "Verificar el codigo que dicta el cliente: unica via para marcar entregado")
    public ResponseEntity<DeliveryInfoDto> verificarOtp(@PathVariable UUID ordenId,
            @Valid @RequestBody VerificarOtpRequestDto request) {
        return ResponseEntity.ok(deliveryService.verificarOtp(ordenId, request));
    }

    @GetMapping("/delivery/tablero")
    @PreAuthorize("hasAnyRole('ADMIN','DELIVERY','MOZO')")
    @Operation(summary = "Pedidos en ruta con transportista y tiempo transcurrido")
    public ResponseEntity<List<DeliveryInfoDto>> tablero() {
        return ResponseEntity.ok(deliveryService.tablero());
    }
}
