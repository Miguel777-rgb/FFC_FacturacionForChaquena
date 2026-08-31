package com.chaquena.backend_logistica.inventario.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cocinado: consume un insumo NO_COCIDO y acredita el COCIDO resultante.
 * El rendimiento suele ser menor que uno (10 kg de pollo crudo no dan 10 kg
 * de pollo horneado), por eso la cantidad obtenida se declara aparte.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransformacionRequestDto {

    @NotNull(message = "El insumo de origen es obligatorio")
    private UUID insumoOrigenId;

    @NotNull(message = "El insumo de destino es obligatorio")
    private UUID insumoDestinoId;

    @NotNull(message = "La cantidad consumida es obligatoria")
    @DecimalMin(value = "0.001", message = "La cantidad consumida debe ser mayor que cero")
    private BigDecimal cantidadConsumida;

    @NotNull(message = "La cantidad obtenida es obligatoria")
    @DecimalMin(value = "0.001", message = "La cantidad obtenida debe ser mayor que cero")
    private BigDecimal cantidadObtenida;

    private String motivoObservacion;
}
