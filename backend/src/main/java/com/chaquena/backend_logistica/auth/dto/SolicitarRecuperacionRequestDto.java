package com.chaquena.backend_logistica.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** El correo al que mandar el enlace, si pertenece a una cuenta activa. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitarRecuperacionRequestDto {

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El formato de correo es invalido")
    private String correo;
}
