package com.chaquena.backend_logistica.mesas.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReglaPlanoTest {

    @Test
    void dosMesasNoPuedenPisarseEnLaMismaZonaPeroSiEnZonasDistintas() {
        ReglaPlano.Posicion m1 = new ReglaPlano.Posicion("M1", "Salon principal", 0, 0, 2, 2);
        ReglaPlano.Posicion m2 = new ReglaPlano.Posicion("M2", " salon principal ", 1, 1, 2, 2);
        ReglaPlano.Posicion t1 = new ReglaPlano.Posicion("T1", "Terraza", 0, 0, 2, 2);

        assertThatThrownBy(() -> ReglaPlano.validar(List.of(m1, m2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M1 y M2");
        assertThatCode(() -> ReglaPlano.validar(List.of(m1, t1))).doesNotThrowAnyException();
    }

    @Test
    void unaMesaNoSeSaleDelPlanoNiMideMasDeCuatroCeldas() {
        assertThatThrownBy(() -> ReglaPlano.validar(List.of(new ReglaPlano.Posicion("M9", null, 11, 0, 2, 2))))
                .hasMessageContaining("se sale del plano");
        assertThatThrownBy(() -> ReglaPlano.validar(List.of(new ReglaPlano.Posicion("M9", null, 0, 0, 5, 1))))
                .hasMessageContaining("entre 1 y 4");
    }

    @Test
    void unaMesaNuevaVaAlPrimerHuecoDejandoPasillo() {
        List<ReglaPlano.Posicion> ocupadas = List.of(
                new ReglaPlano.Posicion("M1", "Salon", 0, 0, 2, 2),
                new ReglaPlano.Posicion("M2", "Salon", 3, 0, 2, 2));

        assertThat(ReglaPlano.primerHueco(List.of(), 2, 2)).isEqualTo(new ReglaPlano.Hueco(0, 0));
        // La columna 2 queda de pasillo entre M1 y M2; la siguiente libre con pasillo es la 6.
        assertThat(ReglaPlano.primerHueco(ocupadas, 2, 2)).isEqualTo(new ReglaPlano.Hueco(6, 0));
    }
}
