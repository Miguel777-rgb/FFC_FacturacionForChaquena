package com.chaquena.backend_logistica.asistencia.service.impl;

import com.chaquena.backend_logistica.asistencia.domain.Marcacion;
import com.chaquena.backend_logistica.asistencia.dto.MiAsistenciaDto;
import com.chaquena.backend_logistica.asistencia.repository.MarcacionRepository;
import com.chaquena.backend_logistica.asistencia.repository.TurnoRepository;
import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.service.TrabajadorContexto;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsistenciaServiceImplTest {

    @Mock private MarcacionRepository marcacionRepository;
    @Mock private TurnoRepository turnoRepository;
    @Mock private TrabajadorContexto trabajadorContexto;

    @InjectMocks
    private AsistenciaServiceImpl servicio;

    private final UUID mozoId = UUID.randomUUID();
    private Trabajador mozo;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("mozo1", null, List.of()));
        mozo = Trabajador.builder().id(mozoId).nombres("Luis").apellidos("Quispe").username("mozo1").activo(true)
                .build();
        when(trabajadorContexto.actual()).thenReturn(Optional.of(mozo));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void laEntradaEsDeQuienTieneLaSesionYQuedaAbierta() {
        ArgumentCaptor<Marcacion> guardada = ArgumentCaptor.forClass(Marcacion.class);
        when(marcacionRepository.findFirstByTrabajadorIdAndSalidaIsNullOrderByEntradaDesc(mozoId))
                .thenReturn(Optional.empty())
                .thenAnswer(i -> Optional.of(guardada.getValue()));
        when(marcacionRepository.save(guardada.capture())).thenAnswer(i -> i.getArgument(0));
        when(turnoRepository.findByTrabajadorIdAndFechaBetweenOrderByFechaAscInicioAsc(eq(mozoId), any(), any()))
                .thenReturn(List.of());

        MiAsistenciaDto mia = servicio.marcarEntrada();

        assertThat(guardada.getValue().getTrabajador()).isSameAs(mozo);
        assertThat(guardada.getValue().getSalida()).isNull();
        assertThat(mia.isDentro()).isTrue();
    }

    @Test
    void noSeEntraDosVecesSinSalir() {
        Marcacion abierta = Marcacion.builder().trabajador(mozo).entrada(ZonedDateTime.now().minusHours(3)).build();
        when(marcacionRepository.findFirstByTrabajadorIdAndSalidaIsNullOrderByEntradaDesc(mozoId))
                .thenReturn(Optional.of(abierta));

        assertThatThrownBy(() -> servicio.marcarEntrada())
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("marca la salida");
        verify(marcacionRepository, never()).save(any());
    }

    @Test
    void noSeSaleSinHaberEntrado() {
        when(marcacionRepository.findFirstByTrabajadorIdAndSalidaIsNullOrderByEntradaDesc(mozoId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.marcarSalida())
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("entrada abierta");
    }
}
