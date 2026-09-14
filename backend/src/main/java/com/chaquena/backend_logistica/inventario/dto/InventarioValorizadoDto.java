package com.chaquena.backend_logistica.inventario.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Cuanto vale lo que hay en el almacen, insumo por insumo.
 *
 * <p>Solo suma lo que tiene costo conocido. Lo que entro sin precio se informa
 * aparte en {@code cantidadSinCosto}: un total que lo contara como cero diria
 * que el almacen vale menos de lo que vale, y nadie lo notaria.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventarioValorizadoDto {

    private BigDecimal valorTotal;

    /** Cuantos insumos tienen stock sin costo registrado. */
    private long insumosConStockSinCosto;

    private List<InsumoValorizado> insumos;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InsumoValorizado {
        private UUID insumoId;
        private String nombre;
        private String unidadMedida;
        private BigDecimal stockActual;
        private BigDecimal valor;
        /** Valor entre cantidad con costo. Nulo si nada de lo que queda tiene costo. */
        private BigDecimal costoPromedio;
        private BigDecimal cantidadSinCosto;
    }
}
