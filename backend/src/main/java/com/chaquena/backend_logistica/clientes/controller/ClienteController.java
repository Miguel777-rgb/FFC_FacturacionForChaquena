package com.chaquena.backend_logistica.clientes.controller;

import com.chaquena.backend_logistica.clientes.dto.*;
import com.chaquena.backend_logistica.clientes.service.ClienteService;
import com.chaquena.backend_logistica.clientes.service.EmpresaService;
import com.chaquena.backend_logistica.pedidos.dto.OrdenResumenDto;
import com.chaquena.backend_logistica.pedidos.service.OrdenService;
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
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Busqueda rapida, registro y preferencias del cliente")
public class ClienteController {

    private final ClienteService clienteService;
    private final EmpresaService empresaService;
    private final OrdenService ordenService;

    @GetMapping("/buscar")
    @Operation(summary = "Busqueda rapida por telefono, documento, nombre o correo")
    public ResponseEntity<List<ClienteResponseDto>> buscar(@RequestParam("q") String termino) {
        return ResponseEntity.ok(clienteService.buscar(termino));
    }

    @GetMapping
    @Operation(summary = "Listado paginado de clientes")
    public ResponseEntity<PageResponseDto<ClienteResponseDto>> listar(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(clienteService.listar(pageable));
    }

    @PostMapping
    @Operation(summary = "Registrar cliente identificado")
    public ResponseEntity<ClienteResponseDto> crear(@Valid @RequestBody ClienteRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.crear(request));
    }

    @PostMapping("/anonimo")
    @Operation(summary = "Registrar cliente que no desea identificarse (documento provisional)")
    public ResponseEntity<ClienteResponseDto> crearAnonimo(
            @Valid @RequestBody ClienteAnonimoRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.crearAnonimo(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ficha del cliente")
    public ResponseEntity<ClienteResponseDto> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(clienteService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar datos del cliente")
    public ResponseEntity<ClienteResponseDto> actualizar(@PathVariable UUID id,
            @Valid @RequestBody ClienteRequestDto request) {
        return ResponseEntity.ok(clienteService.actualizar(id, request));
    }

    @GetMapping("/{id}/preferencias")
    @Operation(summary = "Notas de excepcion de comandas anteriores del cliente")
    public ResponseEntity<PreferenciasClienteDto> preferencias(@PathVariable UUID id) {
        return ResponseEntity.ok(clienteService.preferencias(id));
    }

    @GetMapping("/{id}/ordenes")
    @Operation(summary = "Historial de comandas del cliente")
    public ResponseEntity<List<OrdenResumenDto>> ordenes(@PathVariable UUID id) {
        return ResponseEntity.ok(ordenService.historialDelCliente(id));
    }

    @PatchMapping("/{id}/bloqueo-fraude")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bloquear o desbloquear al cliente por fraude")
    public ResponseEntity<ClienteResponseDto> bloqueoFraude(@PathVariable UUID id,
            @RequestParam boolean bloqueado,
            @RequestParam(required = false) String motivo) {
        return ResponseEntity.ok(clienteService.cambiarBloqueoFraude(id, bloqueado, motivo));
    }

    @GetMapping("/{id}/empresas")
    @Operation(summary = "Empresas a cuyo nombre el cliente pide factura")
    public ResponseEntity<List<EmpresaResponseDto>> empresas(@PathVariable UUID id) {
        return ResponseEntity.ok(empresaService.empresasDelCliente(id));
    }

    @PostMapping("/{id}/empresas")
    @Operation(summary = "Vincular el cliente con una empresa")
    public ResponseEntity<EmpresaResponseDto> vincularEmpresa(@PathVariable UUID id,
            @Valid @RequestBody VincularEmpresaRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(empresaService.vincularConCliente(id, request));
    }
}
