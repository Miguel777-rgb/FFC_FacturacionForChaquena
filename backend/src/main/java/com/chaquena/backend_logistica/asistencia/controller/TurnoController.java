package com.chaquena.backend_logistica.asistencia.controller;

import com.chaquena.backend_logistica.asistencia.dto.TurnoDto;
import com.chaquena.backend_logistica.asistencia.service.TurnoService;
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

/** El horario lo arma el administrador. Cada quien ve los suyos en /asistencia/mia. */
@RestController
@RequestMapping("/api/v1/turnos")
@RequiredArgsConstructor
@Tag(name = "Personal - Turnos", description = "Horario semanal del personal")
public class TurnoController {

    private final TurnoService turnoService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "listarTurnos", summary = "Turnos de los siete dias desde la fecha; sin fecha, la semana de hoy")
    public ResponseEntity<List<TurnoDto>> semana(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {
        return ResponseEntity.ok(turnoService.semana(desde));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "crearTurno", summary = "Asignar un turno; 409 si se pisa con otro de la misma persona")
    public ResponseEntity<TurnoDto> crear(@Valid @RequestBody TurnoDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(turnoService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "actualizarTurno", summary = "Cambiar un turno")
    public ResponseEntity<TurnoDto> actualizar(@PathVariable UUID id, @Valid @RequestBody TurnoDto request) {
        return ResponseEntity.ok(turnoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "eliminarTurno", summary = "Quitar un turno del horario")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        turnoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
