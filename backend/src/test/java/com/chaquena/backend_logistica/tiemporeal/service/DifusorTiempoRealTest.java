package com.chaquena.backend_logistica.tiemporeal.service;

import com.chaquena.backend_logistica.inventario.domain.Insumo;
import com.chaquena.backend_logistica.mesas.domain.Reserva;
import com.chaquena.backend_logistica.pagos.domain.Pago;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.tiemporeal.domain.TemaEnum;
import com.chaquena.backend_logistica.tiemporeal.dto.AvisoTiempoRealDto;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DifusorTiempoRealTest {

    /** Guarda los temas que le llegan en vez de escribirlos en una respuesta. */
    private static class EmisorDePrueba extends SseEmitter {
        final List<TemaEnum> temas = new ArrayList<>();
        boolean cortado;

        @Override
        public void send(SseEventBuilder evento) throws IOException {
            if (cortado) {
                throw new IOException("Broken pipe");
            }
            evento.build().stream()
                    .map(ResponseBodyEmitter.DataWithMediaType::getData)
                    .filter(AvisoTiempoRealDto.class::isInstance)
                    .map(d -> ((AvisoTiempoRealDto) d).tema())
                    .forEach(temas::add);
        }
    }

    private final DifusorTiempoReal difusor = new DifusorTiempoReal();

    @Test
    void cadaCargoRecibeSoloLosTemasDeSusPantallas() {
        EmisorDePrueba cocina = new EmisorDePrueba();
        EmisorDePrueba caja = new EmisorDePrueba();
        EmisorDePrueba almacen = new EmisorDePrueba();
        difusor.registrar(cocina, Set.of("COCINA"));
        difusor.registrar(caja, Set.of("CAJA"));
        difusor.registrar(almacen, Set.of("ALMACEN"));

        // Se cobro una comanda: cambian la orden y su pago.
        difusor.anotar(TemasDeEntidad.de(Orden.class));
        difusor.anotar(TemasDeEntidad.de(Pago.class));
        difusor.difundirPendientes();

        assertThat(cocina.temas).containsExactly(TemaEnum.COCINA);
        assertThat(caja.temas).containsExactlyInAnyOrder(TemaEnum.COMANDAS, TemaEnum.MESAS, TemaEnum.CAJA);
        assertThat(almacen.temas).isEmpty();
    }

    @Test
    void elAdministradorLoRecibeTodo() {
        EmisorDePrueba admin = new EmisorDePrueba();
        difusor.registrar(admin, Set.of("ADMIN"));

        difusor.anotar(Set.of(TemaEnum.values()));
        difusor.difundirPendientes();

        assertThat(admin.temas).containsExactlyInAnyOrder(TemaEnum.values());
    }

    @Test
    void variosCambiosDelMismoTemaSalenEnUnSoloAviso() {
        EmisorDePrueba almacen = new EmisorDePrueba();
        difusor.registrar(almacen, Set.of("ALMACEN"));

        for (int i = 0; i < 5; i++) {
            difusor.anotar(TemasDeEntidad.de(Insumo.class));
        }
        difusor.difundirPendientes();
        difusor.difundirPendientes();

        assertThat(almacen.temas).containsExactly(TemaEnum.STOCK);
    }

    @Test
    void unaReservaMueveLaAgendaYElPlanoPeroNoLaCocina() {
        assertThat(TemasDeEntidad.de(Reserva.class)).containsExactlyInAnyOrder(TemaEnum.RESERVAS, TemaEnum.MESAS);
        assertThat(TemaEnum.COCINA.loRecibe(Set.of("MOZO"))).isFalse();
    }

    @Test
    void laConexionQueFallaAlEscribirSeOlvida() {
        EmisorDePrueba ido = new EmisorDePrueba();
        EmisorDePrueba sigue = new EmisorDePrueba();
        difusor.registrar(ido, Set.of("MOZO"));
        difusor.registrar(sigue, Set.of("MOZO"));
        ido.cortado = true;

        difusor.anotar(Set.of(TemaEnum.MESAS));
        difusor.difundirPendientes();

        assertThat(difusor.conexionesAbiertas()).isEqualTo(1);
        assertThat(sigue.temas).containsExactly(TemaEnum.MESAS);
    }
}
