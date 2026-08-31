package com.chaquena.backend_logistica.cocina.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * El jefe de cocina avisa que un insumo no alcanza para la comanda. Dispara la
 * alerta al mozo, que decide con el cliente entre cambiar, esperar, suplir o
 * cancelar.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportarFaltanteRequestDto {

    @NotNull(message = "El insumo faltante es obligatorio")
    private UUID insumoId;

    @NotBlank(message = "Describe que falta y cuanto, para que el mozo pueda decidir")
    private String detalle;
}
