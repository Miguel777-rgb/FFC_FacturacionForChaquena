package com.chaquena.backend_logistica.fidelizacion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackRequestDto {

    @NotNull(message = "El puntaje de atencion es obligatorio")
    @Min(value = 1, message = "El puntaje minimo es 1")
    @Max(value = 5, message = "El puntaje maximo es 5")
    private Integer puntajeAtencion;

    @NotNull(message = "El puntaje de comida es obligatorio")
    @Min(value = 1, message = "El puntaje minimo es 1")
    @Max(value = 5, message = "El puntaje maximo es 5")
    private Integer puntajeComida;

    @NotNull(message = "El puntaje del lugar es obligatorio")
    @Min(value = 1, message = "El puntaje minimo es 1")
    @Max(value = 5, message = "El puntaje maximo es 5")
    private Integer puntajeLugar;

    private String comentario;
}
