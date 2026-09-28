package com.chaquena.backend_logistica.shared.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Las dos reglas del seeder que no pasan por la base de datos. */
class HistorialDemoSeederTest {

    @Test
    void enSesentaDiasHayDosDiasConFraude() {
        LocalDate hoy = LocalDate.of(2026, 9, 28);
        long conFraude = hoy.minusDays(60).datesUntil(hoy.plusDays(1))
                .filter(HistorialDemoSeeder::diaConFraude).count();

        assertThat(conFraude).isBetween(2L, 3L);
    }

    @Test
    void elLomoSePideMasQueUnPlatoNuevoConTildesOSin() {
        assertThat(HistorialDemoSeeder.pesoDe("Lomo Saltado")).isEqualTo(10);
        assertThat(HistorialDemoSeeder.pesoDe("Ají de Gallina")).isEqualTo(HistorialDemoSeeder.pesoDe("Aji de Gallina"));
        assertThat(HistorialDemoSeeder.pesoDe("Papa a la Huancaína")).isEqualTo(5);
        assertThat(HistorialDemoSeeder.pesoDe("Tacu tacu")).isEqualTo(3);
    }
}
