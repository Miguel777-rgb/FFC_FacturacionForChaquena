package com.chaquena.backend_logistica.auth.controller;

import com.chaquena.backend_logistica.auth.dto.RolResponseDto;
import com.chaquena.backend_logistica.auth.service.RolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalogo de roles del sistema.
 *
 * <p>Es de solo lectura a proposito. Un rol no es un dato de negocio que el
 * local pueda inventar: es la palabra que aparece dentro de los
 * {@code @PreAuthorize} de los controladores, de modo que crear "SUPERVISOR"
 * desde una pantalla no abriria ninguna puerta, solo daria la impresion de
 * haberla abierto. Lo que si se administra es el cargo, que agrupa estos roles
 * y se asigna a las personas.
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Catalogo fijo de roles que reconoce la autorizacion del backend")
public class RolController {

    private final RolService rolService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "listarRoles", summary = "Roles disponibles para armar un cargo")
    public ResponseEntity<List<RolResponseDto>> listar() {
        return ResponseEntity.ok(rolService.listarTodos());
    }
}
