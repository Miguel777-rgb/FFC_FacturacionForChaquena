package com.chaquena.backend_logistica.clientes.controller;

import com.chaquena.backend_logistica.clientes.dto.EmpresaRequestDto;
import com.chaquena.backend_logistica.clientes.dto.EmpresaResponseDto;
import com.chaquena.backend_logistica.clientes.service.EmpresaService;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/empresas")
@RequiredArgsConstructor
@Tag(name = "Clientes - Empresas", description = "Empresas por RUC para comprobantes con factura")
public class EmpresaController {

    private final EmpresaService empresaService;

    @PostMapping
    @Operation(summary = "Registrar empresa")
    public ResponseEntity<EmpresaResponseDto> crear(@Valid @RequestBody EmpresaRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(empresaService.crear(request));
    }

    @GetMapping
    @Operation(summary = "Listar empresas, opcionalmente filtrando por razon social")
    public ResponseEntity<PageResponseDto<EmpresaResponseDto>> listar(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(empresaService.listar(q, pageable));
    }

    @GetMapping("/ruc/{ruc}")
    @Operation(summary = "Buscar empresa por RUC")
    public ResponseEntity<EmpresaResponseDto> porRuc(@PathVariable String ruc) {
        return ResponseEntity.ok(empresaService.obtenerPorRuc(ruc));
    }
}
