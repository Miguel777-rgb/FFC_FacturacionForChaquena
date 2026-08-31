package com.chaquena.backend_logistica.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

/**
 * Apreton de manos cliente-repartidor. Sin el codigo correcto la comanda no
 * puede marcarse como entregada.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificarOtpRequestDto {

    @NotBlank(message = "El codigo de entrega es obligatorio")
    @Pattern(regexp = "\\d{4,6}", message = "El codigo debe tener entre 4 y 6 digitos")
    private String codigo;
}
