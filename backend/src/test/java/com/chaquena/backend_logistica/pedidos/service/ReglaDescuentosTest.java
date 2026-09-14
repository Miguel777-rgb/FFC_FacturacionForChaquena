package com.chaquena.backend_logistica.pedidos.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReglaDescuentosTest {

    private static BigDecimal s(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    void sumaPorcentajeYMontoFijo() {
        assertThat(ReglaDescuentos.descuentoDe(s("80.00"), s("10.00"), s("5.00"))).isEqualByComparingTo("13.00");
        assertThat(ReglaDescuentos.descuentoDe(s("80.00"), null, null)).isEqualByComparingTo("0");
    }

    @Test
    void elNivelSoloGanaSiRebajaAlMenosLoMismoQueElCupon() {
        assertThat(ReglaDescuentos.ganaElNivel(s("5.00"), s("8.00"))).isTrue();
        assertThat(ReglaDescuentos.ganaElNivel(s("8.00"), s("5.00"))).isFalse();
        // En empate el cliente conserva el cupon para otra visita.
        assertThat(ReglaDescuentos.ganaElNivel(s("5.00"), s("5.00"))).isTrue();
        // Un nivel sin rebaja nunca desplaza a un cupon, ni siquiera a uno de cero.
        assertThat(ReglaDescuentos.ganaElNivel(BigDecimal.ZERO, BigDecimal.ZERO)).isFalse();
        assertThat(ReglaDescuentos.ganaElNivel(null, s("3.00"))).isTrue();
    }

    @Test
    void desglosaElIgvIncluidoSinPerderUnCentimo() {
        assertThat(ReglaDescuentos.baseImponible(s("118.00"), s("18.00"))).isEqualByComparingTo("100.00");
        assertThat(ReglaDescuentos.igv(s("118.00"), s("18.00"))).isEqualByComparingTo("18.00");

        BigDecimal total = s("24.00");
        BigDecimal base = ReglaDescuentos.baseImponible(total, s("18.00"));
        BigDecimal igv = ReglaDescuentos.igv(total, s("18.00"));
        assertThat(base).isEqualByComparingTo("20.34");
        assertThat(igv).isEqualByComparingTo("3.66");
        assertThat(base.add(igv)).isEqualByComparingTo(total);
    }

    @Test
    void sinIgvLaBaseEsElTotal() {
        assertThat(ReglaDescuentos.baseImponible(s("58.00"), BigDecimal.ZERO)).isEqualByComparingTo("58.00");
        assertThat(ReglaDescuentos.igv(s("58.00"), null)).isEqualByComparingTo("0");
    }
}
