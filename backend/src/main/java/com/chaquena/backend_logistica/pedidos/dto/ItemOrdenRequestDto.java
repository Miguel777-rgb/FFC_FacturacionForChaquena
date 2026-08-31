package com.chaquena.backend_logistica.pedidos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemOrdenRequestDto {

    @NotNull(message = "El platillo es obligatorio")
    private UUID platilloId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer cantidad;

    /** Personalizacion: "sin cebolla", "bien cocido", "sin aji". */
    private String excepcionesNota;

    @Valid
    private List<ComplementoItemDto> complementos;
}
