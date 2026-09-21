package com.chaquena.backend_logistica.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** El token del enlace y la contrasena nueva. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestablecerPasswordRequestDto {

    @NotBlank(message = "Falta el enlace de recuperacion")
    private String token;

    /**
     * La misma expresion que valida el alta, en la pantalla y en el DTO del
     * registro. Recuperar la contrasena no es ocasion de relajar la regla.
     */
    @NotBlank(message = "La contrasena es obligatoria")
    @Pattern(regexp = "(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}",
            message = "La contrasena lleva ocho caracteres con mayuscula, minuscula y cifra")
    private String password;
}
