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
public class RegistrarTrabajadorRequestDto {

    @NotBlank(message = "El DNI es obligatorio")
    private String dni;

    @NotBlank(message = "Los nombres son obligatorios")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    private String apellidos;

    @Email(message = "El formato de correo es inválido")
    private String correo;

    @NotBlank(message = "El número celular es obligatorio")
    private String celular; // Ej: "51987654321" (con código de país)

    @NotNull(message = "El ID del Cargo es obligatorio")
    private Integer cargoId;

    @NotBlank(message = "El username es obligatorio")
    private String username;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
}