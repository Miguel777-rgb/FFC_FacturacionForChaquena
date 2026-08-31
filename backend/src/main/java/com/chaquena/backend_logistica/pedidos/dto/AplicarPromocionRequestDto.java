package com.chaquena.backend_logistica.pedidos.dto;

import lombok.*;

import java.util.UUID;

/**
 * Aplica o descarta una promocion. Enviar ambos campos en null equivale a
 * quitar el descuento y volver al precio regular.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AplicarPromocionRequestDto {
    private UUID promocionId;
    private String cuponCodigo;
}
