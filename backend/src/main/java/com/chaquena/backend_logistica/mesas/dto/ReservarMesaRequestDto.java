package com.chaquena.backend_logistica.mesas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.ZonedDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservarMesaRequestDto {

    @NotBlank(message = "El nombre de la reserva es obligatorio")
    private String aNombreDe;

    @NotNull(message = "La hora de la reserva es obligatoria")
    private ZonedDateTime para;
}
