package com.chaquena.backend_logistica.mesas.service.impl;

import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.domain.EstadoReservaEnum;
import com.chaquena.backend_logistica.mesas.domain.Mesa;
import com.chaquena.backend_logistica.mesas.domain.Reserva;
import com.chaquena.backend_logistica.mesas.dto.ReservaRequestDto;
import com.chaquena.backend_logistica.mesas.dto.ReservaResponseDto;
import com.chaquena.backend_logistica.mesas.repository.MesaRepository;
import com.chaquena.backend_logistica.mesas.repository.ReservaRepository;
import com.chaquena.backend_logistica.mesas.service.ReglaReservas;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservaServiceImplTest {

    @Mock private ReservaRepository reservaRepository;
    @Mock private MesaRepository mesaRepository;

    @InjectMocks
    private ReservaServiceImpl servicio;

    private final UUID mesaId = UUID.randomUUID();
    private final ZonedDateTime mananaOcho = ZonedDateTime.now(ReglaReservas.ZONA_DEL_LOCAL)
            .plusDays(1).withHour(20).withMinute(0).withSecond(0).withNano(0);
    private Mesa t4;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("mozo1", null, List.of()));
        t4 = Mesa.builder().id(mesaId).numero("T4").zona("Terraza").capacidad(6)
                .estado(EstadoMesaEnum.LIBRE).activa(true).build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private ReservaRequestDto pedido(ZonedDateTime inicio, int personas) {
        return ReservaRequestDto.builder().mesaId(mesaId).nombre("Rosa Quispe").personas(personas).inicio(inicio)
                .build();
    }

    @Test
    void seGuardaPendienteYConLaDuracionPorDefecto() {
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(t4));
        when(reservaRepository.findByMesaIdAndEstadoInAndInicioBetween(eq(mesaId), anyCollection(), any(), any()))
                .thenReturn(List.of());
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));

        ReservaResponseDto guardada = servicio.crear(pedido(mananaOcho, 4));

        assertThat(guardada.getEstado()).isEqualTo(EstadoReservaEnum.PENDIENTE);
        assertThat(guardada.getDuracionMinutos()).isEqualTo(ReglaReservas.DURACION_POR_DEFECTO);
        assertThat(guardada.getFin()).isEqualTo(mananaOcho.plusMinutes(90));
    }

    @Test
    void unaReservaQueSePisaConOtraDeLaMismaMesaDiceConQuienChoca() {
        Reserva zevallos = Reserva.builder().id(UUID.randomUUID()).mesa(t4).nombre("Familia Zevallos").personas(6)
                .inicio(mananaOcho).duracionMinutos(90).estado(EstadoReservaEnum.CONFIRMADA).build();
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(t4));
        when(reservaRepository.findByMesaIdAndEstadoInAndInicioBetween(eq(mesaId), anyCollection(), any(), any()))
                .thenReturn(List.of(zevallos));

        assertThatThrownBy(() -> servicio.crear(pedido(mananaOcho.plusMinutes(60), 2)))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Familia Zevallos")
                .hasMessageContaining("20:00 a 21:30");
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void masPersonasQueSillasOUnaHoraPasadaNoSeReservan() {
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(t4));

        assertThatThrownBy(() -> servicio.crear(pedido(mananaOcho, 8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("es para 6 personas");
        assertThatThrownBy(() -> servicio.crear(pedido(ZonedDateTime.now().minusHours(2), 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pasado");
    }

    @Test
    void unaReservaCumplidaNoVuelveAEstarPendiente() {
        UUID id = UUID.randomUUID();
        Reserva cumplida = Reserva.builder().id(id).mesa(t4).nombre("Rosa").personas(2).inicio(mananaOcho)
                .duracionMinutos(90).estado(EstadoReservaEnum.CUMPLIDA).build();
        when(reservaRepository.findById(id)).thenReturn(Optional.of(cumplida));

        assertThatThrownBy(() -> servicio.cambiarEstado(id, EstadoReservaEnum.PENDIENTE))
                .isInstanceOf(ConflictoException.class);
    }
}
