package com.chaquena.backend_logistica.inventario.controller;

import com.chaquena.backend_logistica.inventario.dto.AlergenoDto;
import com.chaquena.backend_logistica.inventario.service.AlergenoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Leerlos es de cualquier sesion: el POS y la cocina los muestran. Editarlos, de quien arma la carta. */
@RestController
@RequestMapping("/api/v1/alergenos")
@RequiredArgsConstructor
@Tag(name = "Catalogo - Alergenos", description = "Alergenos que se marcan en cada platillo")
public class AlergenoController {

    private final AlergenoService alergenoService;

    @GetMapping
    @Operation(operationId = "listarAlergenos", summary = "Alergenos por nombre; con soloActivos, los que se pueden marcar")
    public ResponseEntity<List<AlergenoDto>> listar(@RequestParam(defaultValue = "false") boolean soloActivos) {
        return ResponseEntity.ok(alergenoService.listar(soloActivos));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(operationId = "crearAlergeno", summary = "Agregar un alergeno al catalogo")
    public ResponseEntity<AlergenoDto> crear(@Valid @RequestBody AlergenoDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alergenoService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(operationId = "actualizarAlergeno", summary = "Renombrar un alergeno")
    public ResponseEntity<AlergenoDto> actualizar(@PathVariable Integer id, @Valid @RequestBody AlergenoDto request) {
        return ResponseEntity.ok(alergenoService.actualizar(id, request));
    }

    @PatchMapping("/{id}/activo")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(operationId = "cambiarActivoAlergeno", summary = "Dar de baja o reactivar un alergeno")
    public ResponseEntity<AlergenoDto> cambiarActivo(@PathVariable Integer id, @RequestParam boolean activo) {
        return ResponseEntity.ok(alergenoService.cambiarActivo(id, activo));
    }
}
