package com.chaquena.backend_logistica.asistencia.controller;

import com.chaquena.backend_logistica.asistencia.dto.DesempenoDto;
import com.chaquena.backend_logistica.asistencia.service.DesempenoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trabajadores")
@RequiredArgsConstructor
@Tag(name = "Personal - Desempeno", description = "Venta, atencion y puntualidad de cada persona")
public class DesempenoController {

    private final DesempenoService desempenoService;

    @GetMapping("/{id}/desempeno")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "desempenoTrabajador", summary = "Comandas, ticket, atencion, tardanzas e inasistencias; sin rango, los ultimos 30 dias")
    public ResponseEntity<DesempenoDto> desempeno(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(desempenoService.de(id, desde, hasta));
    }
}
