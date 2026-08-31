package com.chaquena.backend_logistica.pedidos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplementoItemDto {

    @NotNull(message = "El complemento es obligatorio")
    private UUID complementoId;

    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer cantidad;
}
