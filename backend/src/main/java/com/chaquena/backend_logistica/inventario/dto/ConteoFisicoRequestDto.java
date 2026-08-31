package com.chaquena.backend_logistica.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Conteo fisico de cierre de caja. Se envian las cantidades realmente
 * contadas en almacen; el backend calcula la diferencia contra el sistema y
 * genera un AJUSTE_AUDITORIA por cada insumo descuadrado.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConteoFisicoRequestDto {

    @NotEmpty(message = "El conteo debe incluir al menos un insumo")
    @Valid
    private List<ItemConteo> items;

    private String observacion;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemConteo {

        @NotNull(message = "El insumo es obligatorio")
        private UUID insumoId;

        @NotNull(message = "La cantidad contada es obligatoria")
        @DecimalMin(value = "0.000", message = "La cantidad contada no puede ser negativa")
        private BigDecimal cantidadContada;
    }
}
