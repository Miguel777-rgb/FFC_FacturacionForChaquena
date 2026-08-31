package com.chaquena.backend_logistica.inventario.controller;

import com.chaquena.backend_logistica.inventario.dto.CategoriaRequestDto;
import com.chaquena.backend_logistica.inventario.dto.CategoriaResponseDto;
import com.chaquena.backend_logistica.inventario.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categorias")
@RequiredArgsConstructor
@Tag(name = "Catalogo - Categorias", description = "Categorias de la carta")
public class CategoriaController {

    private final CategoriaService categoriaService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(summary = "Crear categoria")
    public ResponseEntity<CategoriaResponseDto> crear(@Valid @RequestBody CategoriaRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoriaService.crear(request));
    }

    @GetMapping
    @Operation(summary = "Listar categorias de la carta")
    public ResponseEntity<List<CategoriaResponseDto>> listar() {
        return ResponseEntity.ok(categoriaService.listarTodas());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener categoria por id")
    public ResponseEntity<CategoriaResponseDto> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(categoriaService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(summary = "Actualizar categoria")
    public ResponseEntity<CategoriaResponseDto> actualizar(@PathVariable Integer id,
            @Valid @RequestBody CategoriaRequestDto request) {
        return ResponseEntity.ok(categoriaService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar categoria sin platillos asociados")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        categoriaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
