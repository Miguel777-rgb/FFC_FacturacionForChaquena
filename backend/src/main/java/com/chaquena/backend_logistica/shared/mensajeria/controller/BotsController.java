package com.chaquena.backend_logistica.shared.mensajeria.controller;

import com.chaquena.backend_logistica.auth.service.IdentidadBotService;
import com.chaquena.backend_logistica.shared.mensajeria.dto.EstadoBotsDto;
import com.chaquena.backend_logistica.shared.mensajeria.dto.VinculacionBotDto;
import com.chaquena.backend_logistica.shared.mensajeria.service.EstadoBotsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Monitoreo del canal de mensajeria.
 *
 * <p>Es de administrador porque lista quien esta vinculado y permite romper esa
 * vinculacion, que es un permiso operativo: quien queda fuera deja de poder
 * mover stock ni tomar comandas por chat.
 */
@RestController
@RequestMapping("/api/v1/bots")
@RequiredArgsConstructor
@Tag(name = "Bots", description = "Estado del proveedor de mensajeria y cuentas vinculadas")
public class BotsController {

    private final EstadoBotsService estadoBotsService;
    private final IdentidadBotService identidadBotService;

    @GetMapping("/estado")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "estadoBots",
            summary = "Proveedor en servicio, conexion de cada bot y cuentas vinculadas",
            description = "No envia nada al proveedor: lee el estado que el adaptador ya tiene en "
                    + "memoria, de modo que abrir la pantalla no cuesta ni una llamada externa.")
    public ResponseEntity<EstadoBotsDto> estado() {
        return ResponseEntity.ok(estadoBotsService.estado());
    }

    @DeleteMapping("/vinculaciones/{trabajadorId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "desvincularCuentaBot",
            summary = "Liberar la cuenta de mensajeria de un trabajador",
            description = "El trabajador sigue de alta y sigue entrando al front; solo pierde el "
                    + "acceso por chat. Es lo que hay que hacer cuando alguien se vinculo con la "
                    + "cuenta equivocada o deja el local.")
    public ResponseEntity<VinculacionBotDto> desvincular(@PathVariable UUID trabajadorId) {
        return ResponseEntity.ok(VinculacionBotDto.fromEntity(identidadBotService.desvincular(trabajadorId)));
    }
}
