package com.chaquena.backend_logistica.auth.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Alta del primer administrador. Solo funciona con la tabla de trabajadores
 * vacia: resuelve el problema del huevo y la gallina sin dejar abierta el alta
 * publica de trabajadores, que era como estaba antes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BootstrapAdminRequestDto {

    @NotBlank(message = "El DNI es obligatorio")
    private String dni;

    @NotBlank(message = "Los nombres son obligatorios")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    private String apellidos;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El formato de correo es invalido")
    private String correo;

    @NotBlank(message = "El celular es obligatorio")
    private String celular;

    @NotBlank(message = "El username es obligatorio")
    private String username;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres")
    private String password;
}
