package com.chaquena.backend_logistica.pagos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Boton de panico del mozo: billete falso, transferencia adulterada o intento
 * de clonacion de tarjeta.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertaFraudeRequestDto {

    @NotBlank(message = "Describe que se detecto: es lo que ve el administrador en la alerta")
    private String motivo;

    /** Suma al score de riesgo del cliente. Por defecto 50. */
    private Integer puntosRiesgo;

    /** Bloquea al cliente de inmediato ademas de marcar la comanda. */
    private Boolean bloquearCliente;
}
