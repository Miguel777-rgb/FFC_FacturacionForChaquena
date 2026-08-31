package com.chaquena.backend_logistica.auth.controller;

import com.chaquena.backend_logistica.auth.dto.CargoResponseDto;
import com.chaquena.backend_logistica.auth.dto.CrearCargoRequestDto;
import com.chaquena.backend_logistica.auth.service.CargoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Cargos", description = "Cargos del personal y los roles que llevan asociados")
@RestController
@RequestMapping("/api/v1/cargos")
@RequiredArgsConstructor
public class CargoController {

    private final CargoService cargoService;

    @PostMapping
    public ResponseEntity<CargoResponseDto> crear(@Valid @RequestBody CrearCargoRequestDto request) {
        CargoResponseDto response = cargoService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CargoResponseDto>> listarTodos() {
        return ResponseEntity.ok(cargoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CargoResponseDto> obtenerPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(cargoService.obtenerPorId(id));
    }
}