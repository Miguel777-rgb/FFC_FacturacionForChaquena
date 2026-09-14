package com.chaquena.backend_logistica.inventario.service;

import com.chaquena.backend_logistica.inventario.domain.EstadoVencimientoEnum;
import com.chaquena.backend_logistica.inventario.domain.LoteInsumo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReglaLotesTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 14);

    private static BigDecimal n(String valor) {
        return new BigDecimal(valor);
    }

    private static LoteInsumo lote(String inicial, String restante, Integer diasParaVencer, String costo) {
        return LoteInsumo.builder()
                .cantidadInicial(n(inicial))
                .cantidadRestante(n(restante))
                .fechaVencimiento(diasParaVencer != null ? HOY.plusDays(diasParaVencer) : null)
                .costoUnitario(costo != null ? n(costo) : null)
                .build();
    }

    @Test
    void consumePrimeroLoQueVenceAntesYDejaAlFinalLoQueNoTieneFecha() {
        LoteInsumo tarde = lote("5", "5", 10, "2.00");
        LoteInsumo pronto = lote("3", "3", 2, "3.00");
        LoteInsumo sinFecha = lote("4", "4", null, "1.00");

        List<ReglaLotes.Toma> tomas = ReglaLotes.consumirFefo(List.of(tarde, sinFecha, pronto), n("6"));

        assertThat(tomas).extracting(ReglaLotes.Toma::lote).containsExactly(pronto, tarde);
        assertThat(pronto.getCantidadRestante()).isEqualByComparingTo("0");
        assertThat(tarde.getCantidadRestante()).isEqualByComparingTo("2");
        assertThat(sinFecha.getCantidadRestante()).isEqualByComparingTo("4");
        // 3 kg a S/ 3 y 3 kg a S/ 2.
        assertThat(ReglaLotes.costoDe(tomas, n("6"))).isEqualByComparingTo("15.00");
    }

    @Test
    void sinCostoCompletoNoInventaUnCosto() {
        LoteInsumo conCosto = lote("2", "2", 1, "5.00");
        LoteInsumo sinCosto = lote("2", "2", 4, null);

        List<ReglaLotes.Toma> todo = ReglaLotes.consumirFefo(List.of(conCosto, sinCosto), n("3"));
        assertThat(ReglaLotes.costoDe(todo, n("3"))).isNull();

        // Los lotes no alcanzan: lo que falta es stock previo a los lotes, sin costo.
        LoteInsumo unico = lote("1", "1", 1, "5.00");
        List<ReglaLotes.Toma> corto = ReglaLotes.consumirFefo(List.of(unico), n("4"));
        assertThat(corto).hasSize(1);
        assertThat(ReglaLotes.costoDe(corto, n("4"))).isNull();
    }

    @Test
    void loQueSeReponeVuelveAlUltimoLoteConsumidoSinPasarDeSuInicial() {
        LoteInsumo pronto = lote("3", "0", 2, null);
        LoteInsumo tarde = lote("5", "2", 10, null);

        BigDecimal sobra = ReglaLotes.reponer(List.of(pronto, tarde), n("4"));

        assertThat(sobra).isEqualByComparingTo("0");
        assertThat(tarde.getCantidadRestante()).isEqualByComparingTo("5");
        assertThat(pronto.getCantidadRestante()).isEqualByComparingTo("1");

        assertThat(ReglaLotes.reponer(List.of(pronto, tarde), n("5"))).isEqualByComparingTo("3");
        assertThat(pronto.getCantidadRestante()).isEqualByComparingTo("3");
    }

    @Test
    void cuentaElVencimientoEnDiasDelLocal() {
        assertThat(ReglaLotes.estado(HOY.minusDays(1), HOY)).isEqualTo(EstadoVencimientoEnum.VENCIDO);
        assertThat(ReglaLotes.estado(HOY, HOY)).isEqualTo(EstadoVencimientoEnum.POR_VENCER);
        assertThat(ReglaLotes.estado(HOY.plusDays(3), HOY)).isEqualTo(EstadoVencimientoEnum.POR_VENCER);
        assertThat(ReglaLotes.estado(HOY.plusDays(4), HOY)).isEqualTo(EstadoVencimientoEnum.VIGENTE);
        assertThat(ReglaLotes.estado(null, HOY)).isEqualTo(EstadoVencimientoEnum.SIN_VENCIMIENTO);
    }

    @Test
    void resumeLoQueQuedaIgnorandoLosLotesAgotados() {
        List<LoteInsumo> lotes = List.of(
                lote("2", "2", -1, "10.00"),
                lote("3", "1.5", 2, "4.00"),
                lote("6", "6", null, null),
                lote("9", "0", -5, "99.00"));

        ReglaLotes.Resumen r = ReglaLotes.resumir(lotes, HOY);

        assertThat(r.proximoVencimiento()).isEqualTo(HOY.minusDays(1));
        assertThat(r.cantidadVencida()).isEqualByComparingTo("2");
        assertThat(r.cantidadPorVencer()).isEqualByComparingTo("1.5");
        assertThat(r.valor()).isEqualByComparingTo("26.00");
        assertThat(r.cantidadConCosto()).isEqualByComparingTo("3.5");
    }
}
