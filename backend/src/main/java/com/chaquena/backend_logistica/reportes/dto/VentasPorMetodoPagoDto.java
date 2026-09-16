package com.chaquena.backend_logistica.reportes.dto;

import com.chaquena.backend_logistica.pedidos.domain.TipoPagoEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Lo cobrado con un metodo de pago en el rango. Solo cuentan los pagos
 * confirmados: uno pendiente de acreditar todavia puede rechazarse.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentasPorMetodoPagoDto {

    private TipoPagoEnum metodo;
    private long pagos;
    private BigDecimal total;
}
