package com.chaquena.backend_logistica.delivery.controller;

import com.chaquena.backend_logistica.delivery.dto.*;
import com.chaquena.backend_logistica.delivery.service.DeliveryService;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transportistas")
@RequiredArgsConstructor
@Tag(name = "Despacho - Transportistas", description = "Conductores y vehiculos habilitados")
public class TransportistaController {

    private final DeliveryService deliveryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DELIVERY')")
    @Operation(summary = "Registrar transportista con DNI, empresa y telefono")
    public ResponseEntity<TransportistaResponseDto> crear(
            @Valid @RequestBody TransportistaRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deliveryService.crearTransportista(request));
    }

    @GetMapping
    @Operation(summary = "Listar transportistas")
    public ResponseEntity<PageResponseDto<TransportistaResponseDto>> listar(
            @RequestParam(required = false) String empresa,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(deliveryService.listarTransportistas(empresa, pageable));
    }

    @GetMapping("/activos")
    @Operation(summary = "Transportistas activos con sus vehiculos, para asignar un reparto")
    public ResponseEntity<List<TransportistaResponseDto>> activos() {
        return ResponseEntity.ok(deliveryService.transportistasActivos());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DELIVERY')")
    @Operation(summary = "Actualizar datos del transportista")
    public ResponseEntity<TransportistaResponseDto> actualizar(@PathVariable UUID id,
            @Valid @RequestBody TransportistaRequestDto request) {
        return ResponseEntity.ok(deliveryService.actualizarTransportista(id, request));
    }

    @PatchMapping("/{id}/activo")
    @PreAuthorize("hasAnyRole('ADMIN','DELIVERY')")
    @Operation(summary = "Habilitar o dar de baja al transportista")
    public ResponseEntity<TransportistaResponseDto> cambiarActivo(@PathVariable UUID id,
            @RequestParam boolean activo) {
        return ResponseEntity.ok(deliveryService.cambiarActivoTransportista(id, activo));
    }

    @GetMapping("/{id}/vehiculos")
    @Operation(summary = "Vehiculos del transportista")
    public ResponseEntity<List<VehiculoResponseDto>> vehiculos(@PathVariable UUID id) {
        return ResponseEntity.ok(deliveryService.vehiculosDe(id));
    }

    @PostMapping("/{id}/vehiculos")
    @PreAuthorize("hasAnyRole('ADMIN','DELIVERY')")
    @Operation(summary = "Registrar placa, tipo y modelo del vehiculo")
    public ResponseEntity<VehiculoResponseDto> registrarVehiculo(@PathVariable UUID id,
            @Valid @RequestBody VehiculoRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deliveryService.registrarVehiculo(id, request));
    }
}
