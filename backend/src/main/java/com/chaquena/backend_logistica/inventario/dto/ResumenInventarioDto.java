package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.TipoControlInsumoEnum;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumenInventarioDto {

    private long totalInsumos;
    private long insumosBajoMinimo;
    private List<InsumoResponseDto> alertas;
    private List<MovimientosPorTipo> movimientos;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MovimientosPorTipo {
        private TipoControlInsumoEnum tipoControl;
        private long cantidadMovimientos;
        private BigDecimal volumenTotal;
    }
}
