package com.chaquena.backend_logistica.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * Carrito propuesto para validar contra stock antes de crear la comanda.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisponibilidadRequestDto {

    @NotEmpty(message = "Envia al menos un platillo para verificar")
    @Valid
    private List<ItemDisponibilidad> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemDisponibilidad {

        @NotNull(message = "El platillo es obligatorio")
        private UUID platilloId;

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        private Integer cantidad;

        /** Complementos elegidos, que tambien consumen insumo si tienen uno asociado. */
        private List<UUID> complementoIds;
    }
}
