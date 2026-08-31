package com.chaquena.backend_logistica.auth.controller;

import com.chaquena.backend_logistica.auth.dto.ActualizarTrabajadorRequestDto;
import com.chaquena.backend_logistica.auth.dto.RegistrarTrabajadorRequestDto;
import com.chaquena.backend_logistica.auth.dto.TrabajadorResponseDto;
import com.chaquena.backend_logistica.auth.service.TrabajadorService;
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
@RequestMapping("/api/v1/trabajadores")
@RequiredArgsConstructor
@Tag(name = "Trabajadores", description = "Personal del local; el alta requiere cargo administrativo")
public class TrabajadorController {

    private final TrabajadorService trabajadorService;

    /**
     * Antes este endpoint era publico y cualquiera podia darse de alta como
     * trabajador. Ahora exige un token con rol de administrador; el primer
     * usuario del sistema se crea con POST /api/v1/auth/bootstrap.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar trabajador")
    public ResponseEntity<TrabajadorResponseDto> registrar(
            @Valid @RequestBody RegistrarTrabajadorRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trabajadorService.registrar(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar trabajadores")
    public ResponseEntity<PageResponseDto<TrabajadorResponseDto>> listar(
            @RequestParam(required = false) Integer cargoId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(trabajadorService.listar(cargoId, pageable));
    }

    @GetMapping("/activos")
    @Operation(summary = "Trabajadores activos, para asignar mozo o responsable")
    public ResponseEntity<List<TrabajadorResponseDto>> activos() {
        return ResponseEntity.ok(trabajadorService.activos());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener trabajador por id")
    public ResponseEntity<TrabajadorResponseDto> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(trabajadorService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar datos y cargo del trabajador")
    public ResponseEntity<TrabajadorResponseDto> actualizar(@PathVariable UUID id,
            @Valid @RequestBody ActualizarTrabajadorRequestDto request) {
        return ResponseEntity.ok(trabajadorService.actualizar(id, request));
    }

    @PatchMapping("/{id}/activo")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Dar de alta o de baja al trabajador")
    public ResponseEntity<TrabajadorResponseDto> cambiarActivo(@PathVariable UUID id,
            @RequestParam boolean activo) {
        return ResponseEntity.ok(trabajadorService.cambiarActivo(id, activo));
    }
}
