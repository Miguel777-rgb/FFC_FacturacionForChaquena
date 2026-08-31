package com.chaquena.backend_logistica.pedidos.controller;

import com.chaquena.backend_logistica.inventario.dto.PromocionResponseDto;
import com.chaquena.backend_logistica.pedidos.domain.CanalOrigenEnum;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.dto.*;
import com.chaquena.backend_logistica.pedidos.service.OrdenService;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ordenes")
@RequiredArgsConstructor
@Tag(name = "Comandas", description = "Ciclo de vida de la comanda: creacion, edicion y estados")
public class OrdenController {

    private final OrdenService ordenService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(summary = "Crear comanda: valida stock, descuenta insumos y emite evento de facturacion")
    public ResponseEntity<OrdenResponseDto> crear(@Valid @RequestBody CrearOrdenRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ordenService.crear(request));
    }

    @GetMapping
    @Operation(summary = "Listar comandas con filtros y paginacion")
    public ResponseEntity<PageResponseDto<OrdenResumenDto>> buscar(
            @RequestParam(required = false) EstadoOrdenEnum estado,
            @RequestParam(required = false) CanalOrigenEnum canal,
            @RequestParam(required = false) TipoOrdenEnum tipoOrden,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) String mesaNumero,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime hasta,
            @PageableDefault(size = 20, sort = "dateCreated", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ordenService.buscar(estado, canal, tipoOrden, clienteId, mesaNumero,
                desde, hasta, pageable));
    }

    @GetMapping("/activas")
    @Operation(summary = "Comandas abiertas, para el mapa de mesas y el tablero del salon")
    public ResponseEntity<List<OrdenResumenDto>> activas() {
        return ResponseEntity.ok(ordenService.activas());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle completo de la comanda")
    public ResponseEntity<OrdenResponseDto> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(ordenService.obtenerPorId(id));
    }

    @GetMapping("/{id}/ticket")
    @Operation(summary = "Payload de impresion de la comanda de cocina")
    public ResponseEntity<TicketCocinaDto> ticket(@PathVariable UUID id) {
        return ResponseEntity.ok(ordenService.ticket(id));
    }

    @PostMapping("/{id}/detalles")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(summary = "Agregar platillo a una comanda todavia en recepcion")
    public ResponseEntity<OrdenResponseDto> agregarDetalle(@PathVariable UUID id,
            @Valid @RequestBody ItemOrdenRequestDto item) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ordenService.agregarDetalle(id, item));
    }

    @PutMapping("/{id}/detalles/{detalleId}")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(summary = "Cambiar cantidad, complementos o nota de una linea")
    public ResponseEntity<OrdenResponseDto> actualizarDetalle(@PathVariable UUID id,
            @PathVariable UUID detalleId, @Valid @RequestBody ItemOrdenRequestDto item) {
        return ResponseEntity.ok(ordenService.actualizarDetalle(id, detalleId, item));
    }

    @DeleteMapping("/{id}/detalles/{detalleId}")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(summary = "Quitar platillo y devolver sus insumos al stock")
    public ResponseEntity<OrdenResponseDto> eliminarDetalle(@PathVariable UUID id,
            @PathVariable UUID detalleId) {
        return ResponseEntity.ok(ordenService.eliminarDetalle(id, detalleId));
    }

    @GetMapping("/{id}/promociones")
    @Operation(summary = "Promociones aplicables a esta comanda, con el stock del extra ya validado")
    public ResponseEntity<List<PromocionResponseDto>> promociones(@PathVariable UUID id) {
        return ResponseEntity.ok(ordenService.promocionesAplicables(id));
    }

    @PostMapping("/{id}/promociones")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(summary = "Aplicar o descartar promocion y cupon, recalculando el total")
    public ResponseEntity<OrdenResponseDto> aplicarPromocion(@PathVariable UUID id,
            @RequestBody AplicarPromocionRequestDto request) {
        return ResponseEntity.ok(ordenService.aplicarPromocion(id, request));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA','COCINA','DELIVERY')")
    @Operation(summary = "Transicion de estado validada contra la maquina de estados")
    public ResponseEntity<OrdenResponseDto> cambiarEstado(@PathVariable UUID id,
            @Valid @RequestBody CambioEstadoRequestDto request) {
        return ResponseEntity.ok(ordenService.cambiarEstado(id, request));
    }

    @PatchMapping("/{id}/flags")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA','COCINA')")
    @Operation(summary = "Cerrar etapa de recepcion, platillo o despacho con su marca de tiempo")
    public ResponseEntity<OrdenResponseDto> flags(@PathVariable UUID id,
            @RequestBody FlagsRequestDto request) {
        return ResponseEntity.ok(ordenService.actualizarFlags(id, request));
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
    @Operation(summary = "Cancelar con motivo obligatorio y reponer los insumos")
    public ResponseEntity<OrdenResponseDto> cancelar(@PathVariable UUID id,
            @Valid @RequestBody CancelarOrdenRequestDto request) {
        return ResponseEntity.ok(ordenService.cancelar(id, request));
    }
}
