package com.chaquena.backend_logistica.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrearCargoRequestDto {

    @NotBlank(message = "El nombre del cargo es obligatorio")
    private String nombre; // Ej: "Cocinero de Almacén", "Mozo de Salón", "Cajero"

    private String descripcion;

    private List<Integer> rolIds;
}