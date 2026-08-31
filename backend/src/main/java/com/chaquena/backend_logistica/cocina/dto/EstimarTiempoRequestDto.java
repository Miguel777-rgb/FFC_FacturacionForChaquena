package com.chaquena.backend_logistica.cocina.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Lo que cocina promete tardar. El bot de Discord ofrece los tiempos habituales
 * de un toque; este endpoint es la misma operacion para la pantalla web, de modo
 * que la comanda quede igual se fije por donde se fije.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstimarTiempoRequestDto {

    @NotNull(message = "Los minutos son obligatorios")
    @Min(value = 1, message = "El tiempo estimado no puede ser menor a 1 minuto")
    @Max(value = 240, message = "El tiempo estimado no puede superar las 4 horas")
    private Integer minutos;
}
