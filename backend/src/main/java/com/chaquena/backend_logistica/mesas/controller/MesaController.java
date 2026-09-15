package com.chaquena.backend_logistica.mesas.controller;

import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.dto.MesaRequestDto;
import com.chaquena.backend_logistica.mesas.dto.MesaResponseDto;
import com.chaquena.backend_logistica.mesas.dto.PlanoRequestDto;
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
@Tag(name = "Salon - Mesas", description = "Mapa de mesas con estados, plano y reservas")
public class MesaController {

    private final MesaService mesaService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "crearMesa", summary = "Crear mesa en el primer hueco del plano de su zona")
    public ResponseEntity<MesaResponseDto> crear(@Valid @RequestBody MesaRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mesaService.crear(request));
    }

    @GetMapping
    @Operation(summary = "Mapa del salon: mesas activas con su estado, posicion y proxima reserva")
    public ResponseEntity<List<MesaResponseDto>> mapa() {
        return ResponseEntity.ok(mesaService.mapaDelSalon());
    }

    /** Todas las mesas movidas de una vez: el plano se valida entero, no mesa por mesa. */
    @PutMapping("/plano")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "guardarPlanoMesas", summary = "Guardar la posicion, el tamano y la forma de las mesas")
    public ResponseEntity<List<MesaResponseDto>> guardarPlano(@Valid @RequestBody PlanoRequestDto request) {
        return ResponseEntity.ok(mesaService.guardarPlano(request));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "obtenerMesa", summary = "Obtener mesa por id")
    public ResponseEntity<MesaResponseDto> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(mesaService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "actualizarMesa", summary = "Actualizar mesa")
    public ResponseEntity<MesaResponseDto> actualizar(@PathVariable UUID id,
            @Valid @RequestBody MesaRequestDto request) {
        return ResponseEntity.ok(mesaService.actualizar(id, request));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(operationId = "cambiarEstadoMesa", summary = "Cambiar el estado de la mesa (libre, ocupada o inhabilitada)")
    public ResponseEntity<MesaResponseDto> cambiarEstado(@PathVariable UUID id,
            @RequestParam EstadoMesaEnum estado) {
        return ResponseEntity.ok(mesaService.cambiarEstado(id, estado));
    }

    /** Se mantiene por compatibilidad: ahora crea una reserva en /reservas. */
    @PostMapping("/{id}/reservar")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(summary = "Reservar la mesa a nombre de alguien (crea una reserva de 90 minutos)")
    public ResponseEntity<MesaResponseDto> reservar(@PathVariable UUID id,
            @Valid @RequestBody ReservarMesaRequestDto request) {
        return ResponseEntity.ok(mesaService.reservar(id, request));
    }

    @PostMapping("/{id}/liberar")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(summary = "Liberar la mesa y cancelar la reserva que la aparta")
    public ResponseEntity<MesaResponseDto> liberar(@PathVariable UUID id) {
        return ResponseEntity.ok(mesaService.liberar(id));
    }
}
