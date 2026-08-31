package com.chaquena.backend_logistica.reportes.dto;

import com.chaquena.backend_logistica.pedidos.domain.CanalOrigenEnum;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteVentasDto {

    private ZonedDateTime desde;
    private ZonedDateTime hasta;
    private BigDecimal totalVendido;
    private List<PorCanal> porCanal;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PorCanal {
        private CanalOrigenEnum canal;
        private long cantidadComandas;
        private BigDecimal total;
    }
}
