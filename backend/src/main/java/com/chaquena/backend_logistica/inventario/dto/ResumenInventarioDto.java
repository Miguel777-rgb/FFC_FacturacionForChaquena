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

    /** Insumos con algo ya vencido. */
    private long insumosVencidos;

    /** Insumos con algo que vence hoy o dentro de los dias de aviso. */
    private long insumosPorVencer;

    /** Valor del stock con costo conocido, en soles. */
    private BigDecimal valorInventario;

    /** Los insumos bajo minimo. Los vencimientos van por insumo, en su propia respuesta. */
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
