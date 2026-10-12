package com.chaquena.backend_logistica.pedidos.service.impl;

import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.repository.OrdenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * El codigo de entrega en el detalle. Un pedido tomado por telefono no lo
 * recibe por mensaje, asi que quien atiende tiene que poder volver a leerlo;
 * el conductor, que es quien lo pide al entregar, nunca.
 */
@ExtendWith(MockitoExtension.class)
class OrdenServiceImplTest {

    @Mock
    private OrdenRepository ordenRepository;

    @InjectMocks
    private OrdenServiceImpl servicio;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        Orden reparto = Orden.builder()
                .tipoOrden(TipoOrdenEnum.DELIVERY)
                .estado(EstadoOrdenEnum.EN_DESPACHO)
                .codigoOtpEntrega("4821")
                .build();
        when(ordenRepository.findCompletaById(id)).thenReturn(Optional.of(reparto));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    private static void entrarCon(String... autoridades) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "quien", null, AuthorityUtils.createAuthorityList(autoridades)));
    }

    @Test
    void laCajaLoVeParaDictarlo() {
        entrarCon("ROLE_CAJERO", "ROLE_CAJA");

        assertThat(servicio.obtenerPorId(id).getCodigoOtpEntrega()).isEqualTo("4821");
    }

    @Test
    void elMozoTambien() {
        entrarCon("ROLE_MOZO");

        assertThat(servicio.obtenerPorId(id).getCodigoOtpEntrega()).isEqualTo("4821");
    }

    @Test
    void elConductorNo() {
        entrarCon("ROLE_REPARTIDOR", "ROLE_DELIVERY");

        assertThat(servicio.obtenerPorId(id).getCodigoOtpEntrega()).isNull();
    }

    @Test
    void cocinaNiSinSesionTampoco() {
        entrarCon("ROLE_JEFE DE COCINA", "ROLE_COCINA");
        assertThat(servicio.obtenerPorId(id).getCodigoOtpEntrega()).isNull();

        SecurityContextHolder.clearContext();
        assertThat(servicio.obtenerPorId(id).getCodigoOtpEntrega()).isNull();
    }
}
