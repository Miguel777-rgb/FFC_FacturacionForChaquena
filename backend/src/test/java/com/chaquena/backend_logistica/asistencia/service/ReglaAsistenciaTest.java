package com.chaquena.backend_logistica.asistencia.service;

import com.chaquena.backend_logistica.asistencia.domain.EstadoAsistenciaEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReglaAsistenciaTest {

    private final LocalDate lunes = LocalDate.of(2026, 9, 14);
    private final ReglaAsistencia.Tramo doceAOcho = ReglaAsistencia.turno(lunes, LocalTime.of(12, 0), LocalTime.of(20, 0));

    private ReglaAsistencia.Tramo marca(int hora, int minuto, Integer horaSalida) {
        ZonedDateTime entrada = lunes.atTime(hora, minuto).atZone(ReglaAsistencia.ZONA_DEL_LOCAL);
        return new ReglaAsistencia.Tramo(entrada,
                horaSalida == null ? null : lunes.atTime(horaSalida, 0).atZone(ReglaAsistencia.ZONA_DEL_LOCAL));
    }

    @Test
    void diezMinutosDespuesNoEsTardanzaYOnceSiCuentanEnteros() {
        assertThat(ReglaAsistencia.minutosTarde(doceAOcho, marca(12, 10, 20))).isZero();
        assertThat(ReglaAsistencia.minutosTarde(doceAOcho, marca(12, 11, 20))).isEqualTo(11);
    }

    @Test
    void faltarSoloCuentaCuandoElTurnoYaTermino() {
        ZonedDateTime aMitadDeTurno = lunes.atTime(15, 0).atZone(ReglaAsistencia.ZONA_DEL_LOCAL);
        ZonedDateTime alDiaSiguiente = aMitadDeTurno.plusDays(1);

        ReglaAsistencia.Resumen enCurso = ReglaAsistencia.resumir(List.of(doceAOcho), List.of(), aMitadDeTurno);
        assertThat(enCurso.inasistencias()).isZero();
        assertThat(ReglaAsistencia.estado(doceAOcho, null, aMitadDeTurno)).isEqualTo(EstadoAsistenciaEnum.NO_LLEGA);

        ReglaAsistencia.Resumen terminado = ReglaAsistencia.resumir(List.of(doceAOcho), List.of(), alDiaSiguiente);
        assertThat(terminado.inasistencias()).isEqualTo(1);
        assertThat(ReglaAsistencia.estado(doceAOcho, null, alDiaSiguiente)).isEqualTo(EstadoAsistenciaEnum.FALTO);
    }

    @Test
    void elTurnoDeLaNocheTerminaAlDiaSiguienteYChocaConElDeLaManana() {
        ReglaAsistencia.Tramo noche = ReglaAsistencia.turno(lunes, LocalTime.of(22, 0), LocalTime.of(2, 0));
        ReglaAsistencia.Tramo manana = ReglaAsistencia.turno(lunes.plusDays(1), LocalTime.of(1, 0), LocalTime.of(9, 0));

        assertThat(noche.fin().toLocalDate()).isEqualTo(lunes.plusDays(1));
        assertThat(ReglaAsistencia.seSolapan(noche, manana)).isTrue();
    }

    @Test
    void unaEntradaOlvidadaNoSumaHorasYSeCuentaComoSalidaSinMarcar() {
        ZonedDateTime martes = lunes.plusDays(1).atTime(12, 0).atZone(ReglaAsistencia.ZONA_DEL_LOCAL);

        ReglaAsistencia.Resumen resumen = ReglaAsistencia.resumir(List.of(doceAOcho),
                List.of(marca(12, 30, null)), martes);

        assertThat(resumen.asistidos()).isEqualTo(1);
        assertThat(resumen.tardanzas()).isEqualTo(1);
        assertThat(resumen.minutosTarde()).isEqualTo(30);
        assertThat(resumen.minutosTrabajados()).isZero();
        assertThat(resumen.salidasSinMarcar()).isEqualTo(1);
    }
}
