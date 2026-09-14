package com.chaquena.backend_logistica.fidelizacion.service.impl;

import com.chaquena.backend_logistica.fidelizacion.domain.NivelLealtad;
import com.chaquena.backend_logistica.fidelizacion.dto.NivelLealtadDto;
import com.chaquena.backend_logistica.fidelizacion.repository.NivelLealtadRepository;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NivelLealtadServiceImplTest {

    @Mock
    private NivelLealtadRepository repositorio;

    @InjectMocks
    private NivelLealtadServiceImpl servicio;

    private static NivelLealtad nivel(String nombre, int puntos, String porcentaje) {
        return NivelLealtad.builder()
                .id(UUID.randomUUID())
                .nombre(nombre)
                .puntosMinimos(puntos)
                .porcentajeDescuento(new BigDecimal(porcentaje))
                .build();
    }

    private final List<NivelLealtad> niveles = List.of(
            nivel("Bronce", 0, "0.00"),
            nivel("Plata", 5, "5.00"),
            nivel("Oro", 15, "10.00"));

    @Test
    void elNivelEsElMasAltoQueLosPuntosAlcanzan() {
        when(repositorio.findAllByOrderByPuntosMinimosAsc()).thenReturn(niveles);

        assertThat(servicio.nivelDe(7)).map(NivelLealtad::getNombre).contains("Plata");
        assertThat(servicio.siguienteA(7)).map(NivelLealtad::getNombre).contains("Oro");
    }

    @Test
    void justoEnElMinimoYaSeAlcanzaElNivel() {
        when(repositorio.findAllByOrderByPuntosMinimosAsc()).thenReturn(niveles);

        assertThat(servicio.nivelDe(15)).map(NivelLealtad::getNombre).contains("Oro");
        assertThat(servicio.siguienteA(15)).isEmpty();
    }

    @Test
    void sinNivelesNoHayNivelNiSiguiente() {
        when(repositorio.findAllByOrderByPuntosMinimosAsc()).thenReturn(List.of());

        assertThat(servicio.nivelDe(40)).isEmpty();
        assertThat(servicio.siguienteA(40)).isEmpty();
    }

    @Test
    void noDejaDosNivelesDesdeLosMismosPuntos() {
        when(repositorio.findByNombreIgnoreCase("Diamante")).thenReturn(Optional.empty());
        when(repositorio.findByPuntosMinimos(15)).thenReturn(Optional.of(niveles.get(2)));

        NivelLealtadDto pedido = NivelLealtadDto.builder()
                .nombre("Diamante").puntosMinimos(15).porcentajeDescuento(new BigDecimal("12")).build();

        assertThatThrownBy(() -> servicio.crear(pedido))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Oro");
        verify(repositorio, never()).save(any());
    }
}
