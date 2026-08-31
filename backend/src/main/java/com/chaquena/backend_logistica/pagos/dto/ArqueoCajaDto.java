package com.chaquena.backend_logistica.pagos.dto;

import com.chaquena.backend_logistica.pedidos.domain.TipoPagoEnum;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArqueoCajaDto {

    private ZonedDateTime desde;
    private ZonedDateTime hasta;
    private BigDecimal totalCobrado;
    private long cantidadPagos;
    private List<PorMetodo> porMetodo;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PorMetodo {
        private TipoPagoEnum tipoPago;
        private long cantidad;
        private BigDecimal total;
    }
}
