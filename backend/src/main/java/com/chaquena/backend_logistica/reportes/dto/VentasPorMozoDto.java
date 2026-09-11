package com.chaquena.backend_logistica.reportes.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lo vendido por cada mozo en el rango.
 *
 * <p>El nombre puede venir vacio cuando la comanda la tomo alguien que ya no
 * esta dado de alta: se conserva la fila con el identificador antes que
 * descartar la venta del total.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentasPorMozoDto {

    private UUID mozoId;
    private String mozo;
    private long comandas;
    private BigDecimal total;
    private BigDecimal ticketPromedio;
}
