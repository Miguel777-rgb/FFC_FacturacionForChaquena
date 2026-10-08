package com.chaquena.backend_logistica.cocina.service.impl;

import com.chaquena.backend_logistica.cocina.domain.EstadoLlamadoEnum;
import com.chaquena.backend_logistica.cocina.domain.LlamadoCocina;
import com.chaquena.backend_logistica.cocina.dto.LlamadoCocinaDto;
import com.chaquena.backend_logistica.cocina.repository.LlamadoCocinaRepository;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.repository.OrdenRepository;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.websocket.UsuarioStomp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlamadoCocinaServiceImplTest {

    private static final UsuarioStomp CHEF = usuario("chef1", "Julio", "COCINA");
    private static final UsuarioStomp ROSA = usuario("mozo1", "Rosa", "MOZO");

    @Mock
    private LlamadoCocinaRepository llamadoRepository;

    @Mock
    private OrdenRepository ordenRepository;

    @InjectMocks
    private LlamadoCocinaServiceImpl servicio;

    @Test
    void llamarPorUnaComandaListaDejaUnLlamadoPendiente() {
        Orden orden = orden(EstadoOrdenEnum.EN_PREPARACION, true);
        when(ordenRepository.findById(orden.getId())).thenReturn(Optional.of(orden));
        when(llamadoRepository.findFirstByOrdenIdAndEstado(orden.getId(), EstadoLlamadoEnum.PENDIENTE))
                .thenReturn(Optional.empty());
        when(llamadoRepository.save(any(LlamadoCocina.class))).thenAnswer(i -> {
            LlamadoCocina guardado = i.getArgument(0);
            guardado.setId(UUID.randomUUID());
            return guardado;
        });

        LlamadoCocinaDto llamado = servicio.llamar(orden.getId(), CHEF);

        assertThat(llamado.estado()).isEqualTo(EstadoLlamadoEnum.PENDIENTE);
        assertThat(llamado.llamadoPor()).isEqualTo("Julio");
        assertThat(llamado.mesaNumero()).isEqualTo("3");
        assertThat(llamado.atendidoPor()).isNull();
    }

    @Test
    void noSeLlamaPorUnaComandaQueNoEstaLista() {
        Orden orden = orden(EstadoOrdenEnum.EN_PREPARACION, false);
        when(ordenRepository.findById(orden.getId())).thenReturn(Optional.of(orden));

        assertThatThrownBy(() -> servicio.llamar(orden.getId(), CHEF))
                .isInstanceOf(ConflictoException.class);
        verify(llamadoRepository, never()).save(any());
    }

    @Test
    void volverALlamarRepiteElMismoLlamadoEnVezDeDuplicarlo() {
        Orden orden = orden(EstadoOrdenEnum.EN_PREPARACION, true);
        LlamadoCocina pendiente = llamado(orden, EstadoLlamadoEnum.PENDIENTE);
        when(ordenRepository.findById(orden.getId())).thenReturn(Optional.of(orden));
        when(llamadoRepository.findFirstByOrdenIdAndEstado(orden.getId(), EstadoLlamadoEnum.PENDIENTE))
                .thenReturn(Optional.of(pendiente));

        LlamadoCocinaDto llamado = servicio.llamar(orden.getId(), CHEF);

        assertThat(llamado.id()).isEqualTo(pendiente.getId());
        verify(llamadoRepository, never()).save(any());
    }

    @Test
    void elPrimerVoyGanaYQuedaElTiempoDeRespuesta() {
        Orden orden = orden(EstadoOrdenEnum.EN_PREPARACION, true);
        LlamadoCocina atendido = llamado(orden, EstadoLlamadoEnum.ATENDIDO);
        atendido.setAtendidoPorNombre("Rosa");
        atendido.setAtendidoEn(atendido.getLlamadoEn().plusSeconds(12));
        when(llamadoRepository.atender(eq(atendido.getId()), eq("mozo1"), eq("Rosa"), any(),
                eq(EstadoLlamadoEnum.PENDIENTE), eq(EstadoLlamadoEnum.ATENDIDO))).thenReturn(1);
        when(llamadoRepository.findById(atendido.getId())).thenReturn(Optional.of(atendido));

        LlamadoCocinaDto llamado = servicio.atender(atendido.getId(), ROSA);

        assertThat(llamado.atendidoPor()).isEqualTo("Rosa");
        assertThat(llamado.segundosRespuesta()).isEqualTo(12L);
    }

    @Test
    void quienLlegaTardeSabeQuienSeLoLlevo() {
        Orden orden = orden(EstadoOrdenEnum.EN_PREPARACION, true);
        LlamadoCocina yaAtendido = llamado(orden, EstadoLlamadoEnum.ATENDIDO);
        yaAtendido.setAtendidoPorNombre("Rosa");
        UsuarioStomp pedro = usuario("mozo2", "Pedro", "MOZO");
        when(llamadoRepository.atender(eq(yaAtendido.getId()), anyString(), anyString(), any(),
                any(), any())).thenReturn(0);
        when(llamadoRepository.findById(yaAtendido.getId())).thenReturn(Optional.of(yaAtendido));

        assertThatThrownBy(() -> servicio.atender(yaAtendido.getId(), pedro))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Rosa ya va en camino");
    }

    @Test
    void sinLlamadosPendientesNoSeCierraNada() {
        UUID ordenId = UUID.randomUUID();
        when(llamadoRepository.findByOrdenIdAndEstado(ordenId, EstadoLlamadoEnum.PENDIENTE)).thenReturn(List.of());

        assertThat(servicio.cerrarPendientesDe(ordenId)).isEmpty();
        verify(llamadoRepository, never()).cerrarPendientesDe(any(), any(), any(), any(), any());
    }

    @Test
    void alMozoSoloLeLleganLosQueEsperanYACocinaTodos() {
        Orden orden = orden(EstadoOrdenEnum.EN_PREPARACION, true);
        when(llamadoRepository.deComandasEnElPase(EstadoOrdenEnum.EN_PREPARACION, EstadoLlamadoEnum.CERRADO))
                .thenReturn(List.of(llamado(orden, EstadoLlamadoEnum.PENDIENTE),
                        llamado(orden(EstadoOrdenEnum.EN_PREPARACION, true), EstadoLlamadoEnum.ATENDIDO)));

        assertThat(servicio.estadoPara(ROSA)).extracting(LlamadoCocinaDto::estado)
                .containsExactly(EstadoLlamadoEnum.PENDIENTE);
        assertThat(servicio.estadoPara(CHEF)).hasSize(2);
    }

    // -------------------------------------------------------------------------

    private static UsuarioStomp usuario(String username, String nombre, String rol) {
        return new UsuarioStomp(username + "@chaquena.pe", username, nombre, Set.of("ROLE_" + rol),
                Instant.now().plus(1, ChronoUnit.HOURS));
    }

    private static Orden orden(EstadoOrdenEnum estado, boolean lista) {
        return Orden.builder()
                .id(UUID.randomUUID())
                .tipoOrden(TipoOrdenEnum.MESA)
                .mesaNumero("3")
                .estado(estado)
                .flagCierrePlatillo(lista)
                .build();
    }

    private static LlamadoCocina llamado(Orden orden, EstadoLlamadoEnum estado) {
        return LlamadoCocina.builder()
                .id(UUID.randomUUID())
                .orden(orden)
                .llamadoPor("chef1")
                .llamadoPorNombre("Julio")
                .llamadoEn(ZonedDateTime.now().minusMinutes(1))
                .estado(estado)
                .build();
    }
}
