package com.chaquena.backend_logistica.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrarTrabajadorRequestDto {

    /**
     * Las expresiones regulares son las mismas que valida la pantalla en
     * {@code nucleo/validacion/patrones.ts}. Estan repetidas a proposito: la del
     * navegador es comodidad —avisa mientras se escribe— y esta es la que de
     * verdad protege, porque un POST con curl no pasa por el formulario. Si se
     * cambia una, hay que cambiar la otra.
     */
    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "\\d{8}", message = "El DNI son ocho digitos")
    private String dni;

    @NotBlank(message = "Los nombres son obligatorios")
    @Pattern(regexp = "\\p{L}+([ '\u2019-]\\p{L}+)*", message = "Los nombres llevan solo letras")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Pattern(regexp = "\\p{L}+([ '\u2019-]\\p{L}+)*", message = "Los apellidos llevan solo letras")
    private String apellidos;

    @Email(message = "El formato de correo es inválido")
    private String correo;

    @NotBlank(message = "El número celular es obligatorio")
    @Pattern(regexp = "(\\+?51 ?)?9\\d{8}", message = "El celular son nueve digitos y empieza en 9")
    private String celular; // Ej: "51987654321" (con código de país)

    @NotNull(message = "El ID del Cargo es obligatorio")
    private Integer cargoId;

    @NotBlank(message = "El username es obligatorio")
    @Pattern(regexp = "[a-z][a-z0-9._-]{2,19}", message = "El usuario va en minusculas, de 3 a 20 caracteres")
    private String username;

    @NotBlank(message = "La contraseña es obligatoria")
    @Pattern(regexp = "(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}",
            message = "La contrasena lleva ocho caracteres con mayuscula, minuscula y cifra")
    private String password;
}