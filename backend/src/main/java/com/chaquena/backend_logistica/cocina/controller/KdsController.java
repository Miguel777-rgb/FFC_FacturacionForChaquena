package com.chaquena.backend_logistica.cocina.controller;

import com.chaquena.backend_logistica.cocina.dto.ComandaKdsDto;
import com.chaquena.backend_logistica.cocina.dto.EstimarTiempoRequestDto;
import com.chaquena.backend_logistica.cocina.dto.KpisCocinaDto;
import com.chaquena.backend_logistica.cocina.dto.ReportarFaltanteRequestDto;
import com.chaquena.backend_logistica.cocina.service.KdsService;
import com.chaquena.backend_logistica.pedidos.dto.OrdenResponseDto;
import com.chaquena.backend_logistica.shared.dto.MensajeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kds")
@RequiredArgsConstructor
@Tag(name = "Cocina (KDS)", description = "Pantalla de cocina: cola, cronometros y faltantes")
public class KdsController {

    private final KdsService kdsService;

    @GetMapping("/cola")
    @PreAuthorize("hasAnyRole('ADMIN','COCINA','MOZO')")
    @Operation(summary = "Cola de comandas por orden de llegada con tiempo transcurrido")
    public ResponseEntity<List<ComandaKdsDto>> cola() {
        return ResponseEntity.ok(kdsService.cola());
    }

    @PatchMapping("/ordenes/{id}/tomar")
    @PreAuthorize("hasAnyRole('ADMIN','COCINA')")
    @Operation(summary = "Cocina toma la comanda y arranca el cronometro de preparacion")
    public ResponseEntity<OrdenResponseDto> tomar(@PathVariable UUID id) {
        return ResponseEntity.ok(kdsService.tomar(id));
    }

    @PatchMapping("/ordenes/{id}/estimar")
    @PreAuthorize("hasAnyRole('ADMIN','COCINA')")
    @Operation(summary = "Cocina promete un tiempo de preparacion en minutos")
    public ResponseEntity<OrdenResponseDto> estimar(@PathVariable UUID id,
            @Valid @RequestBody EstimarTiempoRequestDto request) {
        return ResponseEntity.ok(kdsService.estimarTiempo(id, request.getMinutos()));
    }

    @PatchMapping("/ordenes/{id}/listo")
    @PreAuthorize("hasAnyRole('ADMIN','COCINA')")
    @Operation(summary = "Comanda completa: cierra el cronometro de platillo")
    public ResponseEntity<OrdenResponseDto> listo(@PathVariable UUID id) {
        return ResponseEntity.ok(kdsService.marcarListo(id));
    }

    @PatchMapping("/detalles/{detalleId}/listo")
    @PreAuthorize("hasAnyRole('ADMIN','COCINA')")
    @Operation(summary = "Marcar un platillo suelto como listo en comandas que salen por partes")
    public ResponseEntity<MensajeDto> detalleListo(@PathVariable UUID detalleId) {
        return ResponseEntity.ok(kdsService.marcarDetalleListo(detalleId));
    }

    @PostMapping("/ordenes/{id}/reportar-faltante")
    @PreAuthorize("hasAnyRole('ADMIN','COCINA')")
    @Operation(summary = "Avisar al mozo que un insumo no alcanza para la comanda")
    public ResponseEntity<MensajeDto> reportarFaltante(@PathVariable UUID id,
            @Valid @RequestBody ReportarFaltanteRequestDto request) {
        return ResponseEntity.ok(kdsService.reportarFaltante(id, request));
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('ADMIN','COCINA')")
    @Operation(summary = "Tiempos promedio por etapa: recepcion, cocina y despacho")
    public ResponseEntity<KpisCocinaDto> kpis() {
        return ResponseEntity.ok(kdsService.kpis());
    }
}
