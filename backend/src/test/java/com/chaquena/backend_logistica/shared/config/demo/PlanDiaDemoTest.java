package com.chaquena.backend_logistica.shared.config.demo;

import com.chaquena.backend_logistica.pedidos.domain.CanalOrigenEnum;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.Catalogo;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.ClienteDemo;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.ComandaPlan;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.Direccion;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.Extra;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.LineaPlan;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.MesaDemo;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.Plato;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.Promo;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.Reparto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El plan es lo que llenan los reportes: si las cuentas no cuadran, el tablero
 * miente; si no es determinista, cada arranque duplica el historial. Por eso se
 * prueba sin base de datos, sobre sesenta dias seguidos.
 */
class PlanDiaDemoTest {

    private static final LocalDate MARTES = LocalDate.of(2026, 9, 15);

    private final Catalogo catalogo = new Catalogo(
            List.of(new Plato(UUID.randomUUID(), new BigDecimal("32.00"), 10),
                    new Plato(UUID.randomUUID(), new BigDecimal("28.00"), 8),
                    new Plato(UUID.randomUUID(), new BigDecimal("14.00"), 5),
                    new Plato(UUID.randomUUID(), new BigDecimal("24.00"), 7)),
            List.of(new Extra(UUID.randomUUID(), new BigDecimal("6.00"))),
            List.of(new MesaDemo(UUID.randomUUID(), "M1", 4), new MesaDemo(UUID.randomUUID(), "T1", 2)),
            new Promo(UUID.randomUUID(), BigDecimal.ZERO, new BigDecimal("5.00")),
            new Promo(UUID.randomUUID(), new BigDecimal("10.00"), BigDecimal.ZERO),
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            List.of(new ClienteDemo(UUID.randomUUID(), "Av. Arequipa 2450, Lince", -12.0868, -77.0345, 5),
                    new ClienteDemo(UUID.randomUUID(), null, null, null, 3)),
            List.of(new Reparto(UUID.randomUUID(), UUID.randomUUID())),
            UUID.randomUUID(),
            List.of(new Direccion("Jr. Tacna 580, Cercado de Lima", -12.0470, -77.0357)),
            new BigDecimal("18.00"));

    private List<ComandaPlan> sesentaDias() {
        List<ComandaPlan> todas = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            LocalDate dia = MARTES.plusDays(i);
            todas.addAll(PlanDiaDemo.planear(dia, catalogo, false));
        }
        return todas;
    }

    @Test
    void seVendeTodosLosDiasTambienElLunes() {
        // «Hoy» no puede quedar en cero en una base de demostracion, sea el dia que sea.
        assertThat(PlanDiaDemo.planear(MARTES.minusDays(1), catalogo, false)).hasSizeBetween(22, 28);
    }

    @Test
    void elMismoDiaDaLasMismasComandasConLosMismosIds() {
        List<ComandaPlan> una = PlanDiaDemo.planear(MARTES, catalogo, false);
        List<ComandaPlan> otra = PlanDiaDemo.planear(MARTES, catalogo, false);

        assertThat(una).extracting(ComandaPlan::id).containsExactlyElementsOf(
                otra.stream().map(ComandaPlan::id).toList());
        assertThat(una).extracting(ComandaPlan::total).containsExactlyElementsOf(
                otra.stream().map(ComandaPlan::total).toList());
    }

    @Test
    void cuantasSegunElDiaDeLaSemana() {
        for (int i = 0; i < 60; i++) {
            LocalDate dia = MARTES.plusDays(i);
            int n = PlanDiaDemo.planear(dia, catalogo, false).size();
            switch (dia.getDayOfWeek()) {
                case MONDAY -> assertThat(n).isBetween(22, 28);
                case TUESDAY, WEDNESDAY, THURSDAY -> assertThat(n).isBetween(25, 32);
                case FRIDAY -> assertThat(n).isBetween(32, 40);
                default -> assertThat(n).isBetween(36, 45);
            }
        }
    }

    @Test
    void lasCuentasCuadranComoEnElServidor() {
        for (ComandaPlan p : sesentaDias()) {
            for (LineaPlan l : p.lineas()) {
                BigDecimal unitario = l.precio().add(l.precioExtra() != null ? l.precioExtra() : BigDecimal.ZERO);
                assertThat(l.subtotal()).isEqualByComparingTo(unitario.multiply(BigDecimal.valueOf(l.cantidad())));
            }
            BigDecimal suma = p.lineas().stream().map(LineaPlan::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(p.subtotal()).isEqualByComparingTo(suma);
            assertThat(p.total()).isEqualByComparingTo(p.subtotal().subtract(p.descuento()));
            assertThat(p.total().signum()).isGreaterThanOrEqualTo(0);
            if (p.pago() != null) {
                assertThat(p.pago().monto()).isEqualByComparingTo(p.total());
                assertThat(p.pago().entregado()).isGreaterThanOrEqualTo(p.total());
            }
        }
    }

    @Test
    void losCronometrosVanEnOrdenYLaCanceladaNoSeCobra() {
        for (ComandaPlan p : sesentaDias()) {
            assertThat(p.fin()).isAfter(p.inicio());
            if (p.estado() == EstadoOrdenEnum.CANCELADO) {
                assertThat(p.pago()).isNull();
                assertThat(p.encuesta()).isNull();
                assertThat(p.motivoCancelacion()).isNotBlank();
                continue;
            }
            assertThat(p.cierreRecepcion()).isAfter(p.inicio());
            assertThat(p.cierrePlatillo()).isAfter(p.inicioCocina());
            assertThat(p.cierreDespacho()).isAfterOrEqualTo(p.cierrePlatillo());
            assertThat(p.fin()).isAfterOrEqualTo(p.cierreDespacho());
            assertThat(p.pago()).isNotNull();
        }
    }

    @Test
    void elBotNoTieneMozoYElDeliveryLlevaDestinoConPunto() {
        List<ComandaPlan> todas = sesentaDias();

        assertThat(todas).filteredOn(p -> p.canal() == CanalOrigenEnum.DISCORD_BOT)
                .isNotEmpty()
                .allSatisfy(p -> assertThat(p.mozoId()).isNull());
        assertThat(todas).filteredOn(p -> p.canal() == CanalOrigenEnum.POS)
                .allSatisfy(p -> assertThat(p.mozoId()).isNotNull());
        assertThat(todas).filteredOn(p -> p.tipo() == TipoOrdenEnum.DELIVERY)
                .isNotEmpty()
                .allSatisfy(p -> {
                    assertThat(p.destino()).isNotNull();
                    assertThat(p.clienteId()).isNotNull();
                    assertThat(p.otp()).hasSize(6);
                });
        assertThat(todas).filteredOn(p -> p.tipo() == TipoOrdenEnum.MESA)
                .allSatisfy(p -> assertThat(p.mesa()).isNotNull());
    }

    @Test
    void hayEncuestasEnCercaDeCuatroDeCadaDiezYAlgunaCritica() {
        List<ComandaPlan> cobradas = sesentaDias().stream()
                .filter(p -> p.estado() == EstadoOrdenEnum.CONCLUIDO).toList();
        long conEncuesta = cobradas.stream().filter(p -> p.encuesta() != null).count();

        assertThat((double) conEncuesta / cobradas.size()).isBetween(0.33, 0.47);
        assertThat(cobradas).anySatisfy(p -> {
            assertThat(p.encuesta()).isNotNull();
            assertThat(p.encuesta().atencion()).isLessThanOrEqualTo(3);
        });
    }

    @Test
    void elDiaDelFraudeTieneUnaSolaCenaConBilleteFalso() {
        LocalDate sabado = MARTES.with(DayOfWeek.SATURDAY);
        List<ComandaPlan> plan = PlanDiaDemo.planear(sabado, catalogo, true);

        assertThat(plan).filteredOn(p -> p.estado() == EstadoOrdenEnum.FRAUDULENTO)
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.inicio().getHour()).isGreaterThanOrEqualTo(19);
                    assertThat(p.pago().fraude()).isTrue();
                    assertThat(p.pago().vuelto()).isEqualByComparingTo("0");
                    assertThat(p.canal()).isEqualTo(CanalOrigenEnum.POS);
                });
    }
}
