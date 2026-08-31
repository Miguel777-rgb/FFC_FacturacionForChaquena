package com.chaquena.backend_logistica.mesas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MesaRequestDto {

    @NotBlank(message = "El numero de mesa es obligatorio")
    @Size(max = 10, message = "El numero de mesa no puede exceder 10 caracteres")
    private String numero;

    @Size(max = 50, message = "La zona no puede exceder 50 caracteres")
    private String zona;

    @Min(value = 1, message = "La capacidad debe ser de al menos una persona")
    private Integer capacidad;

    private Boolean activa;
}
