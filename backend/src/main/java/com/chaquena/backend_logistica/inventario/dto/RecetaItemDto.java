package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.InsumoPlatillo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una linea de la receta (BOM): cuanto de un insumo consume un platillo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecetaItemDto {

    @NotNull(message = "El insumo es obligatorio")
    private UUID insumoId;

    private String insumoNombre;
    private String unidadMedida;

    @NotNull(message = "La cantidad requerida es obligatoria")
    @DecimalMin(value = "0.001", message = "La cantidad requerida debe ser mayor que cero")
    private BigDecimal cantidadRequerida;

    public static RecetaItemDto fromEntity(InsumoPlatillo ip) {
        return RecetaItemDto.builder()
                .insumoId(ip.getInsumo().getId())
                .insumoNombre(ip.getInsumo().getNombre())
                .unidadMedida(ip.getInsumo().getUnidadMedida())
                .cantidadRequerida(ip.getCantidadRequerida())
                .build();
    }
}
