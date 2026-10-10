package com.chaquena.backend_logistica.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecetaRequestDto {

    @NotNull(message = "La receta es obligatoria; envia una lista vacia para dejarla sin insumos")
    @Valid
    private List<RecetaItemDto> insumos;

    /**
     * Los pasos de la preparacion, en orden. Igual que los alergenos del
     * platillo: sin la lista no se tocan, y con una lista vacia se quitan todos.
     */
    private List<@NotBlank(message = "Un paso de la preparacion no puede ir vacio")
            @Size(max = 500, message = "Cada paso de la preparacion admite hasta 500 caracteres") String> pasos;
}
