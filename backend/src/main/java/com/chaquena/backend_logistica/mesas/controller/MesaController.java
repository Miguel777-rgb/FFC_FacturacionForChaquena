package com.chaquena.backend_logistica.mesas.controller;

import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.dto.MesaRequestDto;
import com.chaquena.backend_logistica.mesas.dto.MesaResponseDto;
import com.chaquena.backend_logistica.mesas.dto.ReservarMesaRequestDto;
import com.chaquena.backend_logistica.mesas.service.MesaService;
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
@RequestMapping("/api/v1/mesas")
@RequiredArgsConstructor
@Tag(name = "Salon - Mesas", description = "Mapa de mesas con estados y reservas")
public class MesaController {

    private final MesaService mesaService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear mesa")
    public ResponseEntity<MesaResponseDto> crear(@Valid @RequestBody MesaRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mesaService.crear(request));
    }

    @GetMapping
    @Operation(summary = "Mapa del salon: mesas activas con su estado, por zona")
    public ResponseEntity<List<MesaResponseDto>> mapa() {
        return ResponseEntity.ok(mesaService.mapaDelSalon());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener mesa por id")
    public ResponseEntity<MesaResponseDto> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(mesaService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar mesa")
    public ResponseEntity<MesaResponseDto> actualizar(@PathVariable UUID id,
            @Valid @RequestBody MesaRequestDto request) {
        return ResponseEntity.ok(mesaService.actualizar(id, request));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(summary = "Cambiar el estado de la mesa")
    public ResponseEntity<MesaResponseDto> cambiarEstado(@PathVariable UUID id,
            @RequestParam EstadoMesaEnum estado) {
        return ResponseEntity.ok(mesaService.cambiarEstado(id, estado));
    }

    @PostMapping("/{id}/reservar")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(summary = "Reservar la mesa a nombre de alguien")
    public ResponseEntity<MesaResponseDto> reservar(@PathVariable UUID id,
            @Valid @RequestBody ReservarMesaRequestDto request) {
        return ResponseEntity.ok(mesaService.reservar(id, request));
    }

    @PostMapping("/{id}/liberar")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(summary = "Liberar la mesa")
    public ResponseEntity<MesaResponseDto> liberar(@PathVariable UUID id) {
        return ResponseEntity.ok(mesaService.liberar(id));
    }
}
