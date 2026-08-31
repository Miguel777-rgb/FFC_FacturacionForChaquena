package com.chaquena.backend_logistica.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActualizarTrabajadorRequestDto {

    @NotBlank(message = "Los nombres son obligatorios")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    private String apellidos;

    @Email(message = "El formato de correo es invalido")
    private String correo;

    private String celular;

    @NotNull(message = "El cargo es obligatorio")
    private Integer cargoId;
}
