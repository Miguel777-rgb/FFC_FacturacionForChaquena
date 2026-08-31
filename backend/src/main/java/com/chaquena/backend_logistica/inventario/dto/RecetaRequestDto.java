package com.chaquena.backend_logistica.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
}
