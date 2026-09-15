package com.chaquena.backend_logistica.mesas.controller;

import com.chaquena.backend_logistica.mesas.domain.EstadoReservaEnum;
import com.chaquena.backend_logistica.mesas.dto.ReservaRequestDto;
import com.chaquena.backend_logistica.mesas.dto.ReservaResponseDto;
import com.chaquena.backend_logistica.mesas.service.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** La agenda es de quien recibe al comensal: el mozo, la caja y el administrador. */
@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
@Tag(name = "Salon - Reservas", description = "Agenda de reservas por mesa")
public class ReservaController {

    private final ReservaService reservaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(operationId = "listarReservas", summary = "Reservas de un dia por hora; sin dia, las de hoy en Lima")
    public ResponseEntity<List<ReservaResponseDto>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
        return ResponseEntity.ok(reservaService.listarDelDia(dia));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(operationId = "crearReserva", summary = "Reservar una mesa; 409 si ya esta apartada a esa hora")
    public ResponseEntity<ReservaResponseDto> crear(@Valid @RequestBody ReservaRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.crear(request));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(operationId = "cambiarEstadoReserva", summary = "Confirmar, cumplir, cancelar o marcar que no asistio")
    public ResponseEntity<ReservaResponseDto> cambiarEstado(@PathVariable UUID id,
            @RequestParam EstadoReservaEnum estado) {
        return ResponseEntity.ok(reservaService.cambiarEstado(id, estado));
    }
}
