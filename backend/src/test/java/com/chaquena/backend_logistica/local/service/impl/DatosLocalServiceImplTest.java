package com.chaquena.backend_logistica.local.service.impl;

import com.chaquena.backend_logistica.local.domain.DatosLocal;
import com.chaquena.backend_logistica.local.dto.DatosLocalDto;
import com.chaquena.backend_logistica.local.dto.HorarioLocalDto;
import com.chaquena.backend_logistica.local.repository.DatosLocalRepository;
import com.chaquena.backend_logistica.local.repository.HorarioLocalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatosLocalServiceImplTest {

    @Mock
    private DatosLocalRepository datosRepositorio;

    @Mock
    private HorarioLocalRepository horarioRepositorio;

    @InjectMocks
    private DatosLocalServiceImpl servicio;

    @Test
    void laPrimeraVezCreaElIgvPorDefectoYLosSieteDiasDeLunesADomingo() {
        when(datosRepositorio.findById(DatosLocal.ID_UNICO)).thenReturn(Optional.empty());
        when(datosRepositorio.save(any(DatosLocal.class))).thenAnswer(i -> i.getArgument(0));
        when(horarioRepositorio.findAll()).thenReturn(List.of());
        when(horarioRepositorio.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        DatosLocalDto datos = servicio.obtener();

        assertThat(datos.getPorcentajeIgv()).isEqualByComparingTo("18.00");
        assertThat(datos.getHorarios()).extracting(HorarioLocalDto::getDia)
                .containsExactly(DayOfWeek.values());
        // Sin definir no es cerrado: nadie dijo todavia que ese dia no se abre.
        assertThat(datos.getHorarios()).allSatisfy(h -> {
            assertThat(h.getCerrado()).isFalse();
            assertThat(h.getAbre()).isNull();
        });
    }

    @Test
    void rechazaUnDiaConHoraDeAperturaPeroSinCierreAntesDeGuardarNada() {
        DatosLocalDto cambios = DatosLocalDto.builder()
                .nombreComercial("Chaquena")
                .horarios(List.of(HorarioLocalDto.builder()
                        .dia(DayOfWeek.FRIDAY).cerrado(false).abre(LocalTime.of(12, 0)).build()))
                .build();

        assertThatThrownBy(() -> servicio.actualizar(cambios))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("viernes");
        verify(datosRepositorio, never()).save(any());
    }

    @Test
    void rechazaElMismoDiaDosVeces() {
        HorarioLocalDto lunes = HorarioLocalDto.builder().dia(DayOfWeek.MONDAY).cerrado(true).build();
        DatosLocalDto cambios = DatosLocalDto.builder().horarios(List.of(lunes, lunes)).build();

        assertThatThrownBy(() -> servicio.actualizar(cambios))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dos veces");
    }
}
