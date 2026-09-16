package com.chaquena.backend_logistica.asistencia.controller;

import com.chaquena.backend_logistica.asistencia.dto.AsistenciaDelDiaDto;
import com.chaquena.backend_logistica.asistencia.dto.MiAsistenciaDto;
import com.chaquena.backend_logistica.asistencia.service.AsistenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Marcar es de cualquier sesion y siempre sobre si misma: los endpoints no
 * reciben a quien, lo sacan del token. Ver la asistencia de todos es del
 * administrador.
 */
@RestController
@RequestMapping("/api/v1/asistencia")
@RequiredArgsConstructor
@Tag(name = "Personal - Asistencia", description = "Entradas, salidas y asistencia del dia")
public class AsistenciaController {

    private final AsistenciaService asistenciaService;

    @PostMapping("/entrada")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "marcarEntrada", summary = "Marcar mi entrada; 409 si ya tengo una abierta")
    public ResponseEntity<MiAsistenciaDto> entrada() {
        return ResponseEntity.ok(asistenciaService.marcarEntrada());
    }

    @PostMapping("/salida")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "marcarSalida", summary = "Marcar mi salida; 409 si no tengo una entrada abierta")
    public ResponseEntity<MiAsistenciaDto> salida() {
        return ResponseEntity.ok(asistenciaService.marcarSalida());
    }

    @GetMapping("/mia")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "miAsistencia", summary = "Si estoy dentro y mis turnos de la semana")
    public ResponseEntity<MiAsistenciaDto> mia() {
        return ResponseEntity.ok(asistenciaService.mia());
    }

    @GetMapping("/dia")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "asistenciaDelDia", summary = "Cada turno del dia con su entrada y su salida; sin dia, hoy")
    public ResponseEntity<List<AsistenciaDelDiaDto>> delDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
        return ResponseEntity.ok(asistenciaService.delDia(dia));
    }
}
