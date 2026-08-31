package com.chaquena.backend_logistica.pedidos.dto;

import lombok.*;

/**
 * Cierre de etapa. Cada flag sella tambien su marca de tiempo, que es lo que
 * alimenta los cronometros de auditoria operativa.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlagsRequestDto {
    private Boolean cierreRecepcion;
    private Boolean cierrePlatillo;
    private Boolean cierreDespacho;
}
