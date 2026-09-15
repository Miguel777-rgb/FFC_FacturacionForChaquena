package com.chaquena.backend_logistica.mesas.service;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static com.chaquena.backend_logistica.mesas.domain.EstadoReservaEnum.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReglaReservasTest {

    private final ZonedDateTime ocho = ZonedDateTime.of(2026, 9, 14, 20, 0, 0, 0, ReglaReservas.ZONA_DEL_LOCAL);

    @Test
    void dosReservasSePisanSiUnaEmpiezaAntesDeQueTermineLaOtra() {
        assertThat(ReglaReservas.seSolapan(ocho, 90, ocho.plusMinutes(60), 90)).isTrue();
        // Terminar a las 21:30 y empezar a las 21:30 no es un choque.
        assertThat(ReglaReservas.seSolapan(ocho, 90, ocho.plusMinutes(90), 90)).isFalse();
        assertThat(ReglaReservas.seSolapan(ocho.plusMinutes(90), 60, ocho, 90)).isFalse();
    }

    @Test
    void laMesaSeApartaUnaHoraAntesYHastaQueTerminaLaReserva() {
        assertThat(ReglaReservas.apartaLaMesa(PENDIENTE, ocho, 90, ocho.minusMinutes(61))).isFalse();
        assertThat(ReglaReservas.apartaLaMesa(PENDIENTE, ocho, 90, ocho.minusMinutes(60))).isTrue();
        assertThat(ReglaReservas.apartaLaMesa(CONFIRMADA, ocho, 90, ocho.plusMinutes(89))).isTrue();
        assertThat(ReglaReservas.apartaLaMesa(CONFIRMADA, ocho, 90, ocho.plusMinutes(90))).isFalse();
    }

    @Test
    void unaReservaCerradaNoApartaNadaNiCambiaDeEstado() {
        assertThat(ReglaReservas.apartaLaMesa(CANCELADA, ocho, 90, ocho)).isFalse();
        assertThat(ReglaReservas.apartaLaMesa(CUMPLIDA, ocho, 90, ocho)).isFalse();
        assertThat(ReglaReservas.transiciones(CUMPLIDA)).isEmpty();
        assertThat(ReglaReservas.transiciones(NO_ASISTIO)).isEmpty();
        assertThat(ReglaReservas.transiciones(CONFIRMADA)).doesNotContain(PENDIENTE, CONFIRMADA);
    }
}
