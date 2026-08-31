package com.chaquena.backend_logistica.delivery.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsignarDeliveryRequestDto {

    @NotNull(message = "El transportista es obligatorio")
    private UUID transportistaId;

    /** Vehiculo con el que sale. Debe pertenecer al transportista asignado. */
    @NotNull(message = "El vehiculo es obligatorio: la placa se audita antes de despachar")
    private UUID vehiculoId;

    @Min(value = 1, message = "El tiempo estimado debe ser de al menos un minuto")
    private Integer tiempoEstimadoMinutos;
}
