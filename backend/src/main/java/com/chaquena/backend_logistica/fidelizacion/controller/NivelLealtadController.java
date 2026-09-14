package com.chaquena.backend_logistica.fidelizacion.controller;

import com.chaquena.backend_logistica.fidelizacion.dto.NivelLealtadDto;
import com.chaquena.backend_logistica.fidelizacion.service.NivelLealtadService;
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

/**
 * La lista es de cualquier sesion: el POS la necesita para decirle al mozo que
 * descuento tiene el cliente que acaba de identificar. Definir los niveles es
 * del administrador.
 */
@RestController
@RequestMapping("/api/v1/niveles-lealtad")
@RequiredArgsConstructor
@Tag(name = "Niveles de lealtad", description = "Escalones del programa de lealtad por puntos")
public class NivelLealtadController {

    private final NivelLealtadService nivelService;

    @GetMapping
    @Operation(operationId = "listarNivelesLealtad", summary = "Niveles de lealtad, de menos a mas puntos")
    public ResponseEntity<List<NivelLealtadDto>> listar() {
        return ResponseEntity.ok(nivelService.listar());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "crearNivelLealtad", summary = "Crear un nivel de lealtad")
    public ResponseEntity<NivelLealtadDto> crear(@Valid @RequestBody NivelLealtadDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nivelService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "actualizarNivelLealtad", summary = "Actualizar un nivel de lealtad")
    public ResponseEntity<NivelLealtadDto> actualizar(@PathVariable UUID id,
            @Valid @RequestBody NivelLealtadDto request) {
        return ResponseEntity.ok(nivelService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "eliminarNivelLealtad", summary = "Eliminar un nivel de lealtad")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        nivelService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
