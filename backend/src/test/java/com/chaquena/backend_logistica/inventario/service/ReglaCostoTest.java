package com.chaquena.backend_logistica.inventario.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReglaCostoTest {

    private final UUID pollo = UUID.randomUUID();
    private final UUID papa = UUID.randomUUID();

    private final List<ReglaCosto.Linea> receta = List.of(
            new ReglaCosto.Linea(pollo, "Pechuga de pollo", new BigDecimal("0.250")),
            new ReglaCosto.Linea(papa, "Papa amarilla", new BigDecimal("0.300")));

    @Test
    void sumaLaRecetaPorElUltimoCostoYDiceCuantoDeja() {
        ReglaCosto.Costo costo = ReglaCosto.calcular(new BigDecimal("25.00"), receta,
                Map.of(pollo, new BigDecimal("11.50"), papa, new BigDecimal("2.00")));

        // 0.25 x 11.50 + 0.30 x 2.00 = 3.475, redondeado a centimos.
        assertThat(costo.costo()).isEqualByComparingTo("3.48");
        assertThat(costo.margen()).isEqualByComparingTo("21.52");
        assertThat(costo.margenPorcentaje()).isEqualByComparingTo("86.1");
        assertThat(costo.insumosSinCosto()).isEmpty();
    }

    @Test
    void siUnInsumoNuncaEntroConCostoNoSeInventaUnCostoParcial() {
        ReglaCosto.Costo costo = ReglaCosto.calcular(new BigDecimal("25.00"), receta,
                Map.of(pollo, new BigDecimal("11.50")));

        assertThat(costo.costo()).isNull();
        assertThat(costo.margen()).isNull();
        assertThat(costo.insumosSinCosto()).containsExactly("Papa amarilla");
    }

    @Test
    void sinRecetaNoHayCostoNiInsumosQueReclamar() {
        ReglaCosto.Costo costo = ReglaCosto.calcular(new BigDecimal("6.00"), List.of(), Map.of());

        assertThat(costo.costo()).isNull();
        assertThat(costo.insumosSinCosto()).isEmpty();
    }

    @Test
    void unPlatilloDePrecioCeroTieneCostoPeroNoPorcentaje() {
        ReglaCosto.Costo costo = ReglaCosto.calcular(BigDecimal.ZERO, receta,
                Map.of(pollo, new BigDecimal("11.50"), papa, new BigDecimal("2.00")));

        assertThat(costo.costo()).isEqualByComparingTo("3.48");
        assertThat(costo.margen()).isEqualByComparingTo("-3.48");
        assertThat(costo.margenPorcentaje()).isNull();
    }
}
