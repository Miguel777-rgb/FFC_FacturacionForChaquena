package com.chaquena.backend_logistica.shared.mensajeria;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Elige que adaptador de mensajeria esta en servicio y le pasa todo lo que
 * salga de los bots.
 *
 * <p>Deliberadamente no implementa {@link MensajeriaPort}: si lo hiciera,
 * Spring lo inyectaria dentro de su propia lista de adaptadores. Es un
 * despachador, no un proveedor.
 *
 * <p>Cuando el proveedor configurado no existe o no tiene credenciales, se
 * queda con el adaptador nulo en lugar de reventar al arrancar: en desarrollo
 * se levanta el backend sin tokens y los bots simplemente no envian nada,
 * mientras el resto del sistema (POS, KDS, reportes) funciona igual.
 */
@Service
@Slf4j
public class MensajeriaService {

    private final Map<String, MensajeriaPort> adaptadores;
    private final String proveedorPedido;
    private final MensajeriaPort activo;

    public MensajeriaService(List<MensajeriaPort> disponibles,
            @Value("${app.mensajeria.proveedor:discord}") String proveedorPedido) {
        this.adaptadores = disponibles.stream()
                .collect(Collectors.toMap(MensajeriaPort::nombre, Function.identity()));
        this.proveedorPedido = proveedorPedido == null ? "" : proveedorPedido.trim().toLowerCase();
        this.activo = adaptadores.getOrDefault(this.proveedorPedido, MensajeriaNula.INSTANCIA);
    }

    /**
     * Deja escrito en el log que proveedor quedo en servicio.
     *
     * <p>Se hace al final del arranque y no al construir el bean: las conexiones
     * de Discord se abren en {@code ApplicationReadyEvent} con orden anterior a
     * este, asi que preguntarlo antes daria siempre "sin credenciales" aunque las
     * hubiera.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    void anunciar() {
        if (activo == MensajeriaNula.INSTANCIA) {
            log.warn("⚠️ app.mensajeria.proveedor='{}' no corresponde a ningun adaptador. "
                    + "Adaptadores disponibles: {}. Los bots quedan mudos.",
                    proveedorPedido, adaptadores.keySet());
            return;
        }
        if (!activo.disponible()) {
            log.warn("⚠️ Adaptador de mensajeria '{}' activo pero sin credenciales completas. "
                    + "Los bots no enviaran nada hasta que se configuren.", activo.nombre());
            return;
        }
        log.info("✉️ Mensajeria por '{}' (hasta {} opciones por lista).",
                activo.nombre(), activo.maxOpciones());
    }

    public int maxOpciones() {
        return activo.maxOpciones();
    }

    public void enviarTexto(CanalBot bot, DestinoBot destino, String texto) {
        activo.enviarTexto(bot, destino, texto);
    }

    public void enviarOpciones(CanalBot bot, DestinoBot destino, String titulo, String cuerpo,
            String textoBoton, List<OpcionBot> opciones) {
        activo.enviarOpciones(bot, destino, titulo, cuerpo, textoBoton, opciones);
    }

    public void enviarBotones(CanalBot bot, DestinoBot destino, String cuerpo, List<BotonBot> botones) {
        activo.enviarBotones(bot, destino, cuerpo, botones);
    }

    public void acusarRecibo(CanalBot bot, String messageId) {
        activo.acusarRecibo(bot, messageId);
    }

    /** Adaptador de reserva: traga los envios y los deja anotados en el log. */
    private static final class MensajeriaNula implements MensajeriaPort {

        private static final MensajeriaNula INSTANCIA = new MensajeriaNula();

        @Override
        public String nombre() {
            return "ninguno";
        }

        @Override
        public int maxOpciones() {
            return 10;
        }

        @Override
        public boolean disponible() {
            return false;
        }

        @Override
        public void enviarTexto(CanalBot bot, DestinoBot destino, String texto) {
            log.debug("[{}] Sin proveedor de mensajeria. No se envio a {}: {}", bot, destino.id(), texto);
        }

        @Override
        public void enviarOpciones(CanalBot bot, DestinoBot destino, String titulo, String cuerpo,
                String textoBoton, List<OpcionBot> opciones) {
            log.debug("[{}] Sin proveedor de mensajeria. No se envio la lista '{}' a {}.",
                    bot, titulo, destino.id());
        }

        @Override
        public void enviarBotones(CanalBot bot, DestinoBot destino, String cuerpo, List<BotonBot> botones) {
            log.debug("[{}] Sin proveedor de mensajeria. No se enviaron botones a {}.", bot, destino.id());
        }
    }
}
