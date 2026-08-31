package com.chaquena.backend_logistica.cocina.bot.service;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.service.IdentidadBotService;
import com.chaquena.backend_logistica.cocina.service.KdsService;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.pedidos.domain.OrdenCreadaEvent;
import com.chaquena.backend_logistica.pedidos.domain.OrdenDetalle;
import com.chaquena.backend_logistica.pedidos.domain.OrdenDetalleComplemento;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.repository.OrdenRepository;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.mensajeria.BotonBot;
import com.chaquena.backend_logistica.shared.mensajeria.EmisorBotCliente;
import com.chaquena.backend_logistica.shared.mensajeria.EmisorBotInterno;
import com.chaquena.backend_logistica.shared.mensajeria.MensajeBot;
import com.chaquena.backend_logistica.shared.mensajeria.discord.DiscordProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * El tablero de cocina dentro del chat.
 *
 * <p>Cuando entra una comanda —del mozo, del cliente o del POS— se publica en el
 * canal de cocina con los botones que el cocinero necesita: tomarla, prometer un
 * tiempo y darla por lista. Cada boton llama al mismo {@link KdsService} que usa
 * la pantalla web, asi que los cronometros y los KPIs salen iguales se opere por
 * donde se opere.
 *
 * <p>Publicar en un canal en vez de escribirle a una persona es lo que Discord
 * permite y WhatsApp no. Importa mas de lo que parece: la comanda no depende de
 * quien este de turno ni de que alguien tenga el movil a mano, y el aviso queda
 * a la vista de toda la cocina.
 *
 * <p>El tiempo que fija el cocinero viaja de vuelta al cliente por el bot de
 * clientes. Es el unico punto donde los dos bots se tocan, y lo hacen a traves
 * de sus emisores, nunca compartiendo cuenta.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CocinaBotService {

    /** Prefijo de todo lo que este flujo emite; el listener enruta por el. */
    public static final String PREFIJO = "cocina:";
    private static final String ACCION_TOMAR = PREFIJO + "tomar:";
    private static final String ACCION_ETA = PREFIJO + "eta:";
    private static final String ACCION_LISTO = PREFIJO + "listo:";

    /** Los tiempos que se ofrecen de un toque. Cubren casi toda la carta. */
    private static final List<Integer> MINUTOS_SUGERIDOS = List.of(10, 15, 20, 30, 45);

    private final OrdenRepository ordenRepository;
    private final KdsService kdsService;
    private final IdentidadBotService identidad;
    private final EmisorBotInterno emisorInterno;
    private final EmisorBotCliente emisorCliente;
    /**
     * Opcional a proposito: con {@code app.mensajeria.proveedor=whatsapp} no hay
     * configuracion de Discord y por tanto no hay canal de cocina. El resto del
     * sistema sigue funcionando; solo se pierde el aviso.
     */
    private final ObjectProvider<DiscordProperties> discord;

    /** Si este mensaje le corresponde a este flujo. */
    public boolean atiende(String contenido) {
        return contenido != null && contenido.startsWith(PREFIJO);
    }

    // =====================================================================
    // Aviso a cocina
    // =====================================================================

    /**
     * La transaccion tiene que ser nueva ({@code REQUIRES_NEW}): la de la venta
     * ya esta confirmada cuando llega este aviso, y los complementos de cada
     * linea se cargan de forma diferida. Sin una sesion abierta, la tarjeta de
     * cocina reventaria justo al leer lo que mas le importa al cocinero.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void avisarDeComandaNueva(OrdenCreadaEvent evento) {
        Optional<String> canal = canalCocina();
        if (canal.isEmpty()) {
            log.debug("Sin canal de cocina configurado: la comanda {} no se publico.", evento.ordenId());
            return;
        }

        Optional<Orden> orden = ordenRepository.findCompletaById(evento.ordenId());
        if (orden.isEmpty()) {
            log.warn("La comanda {} ya no existe al publicarla en cocina.", evento.ordenId());
            return;
        }

        try {
            emisorInterno.publicarEnCanal(canal.get(), tarjetaDe(orden.get()));
            emisorInterno.publicarBotonesEnCanal(canal.get(),
                    "¿Qué hacemos con la **" + cortoDe(orden.get().getId()) + "**?",
                    botonesDe(orden.get().getId()));
        } catch (Exception e) {
            // Un aviso que no sale no puede deshacer una venta ya cerrada.
            log.error("No se pudo publicar la comanda {} en cocina: {}", evento.ordenId(), e.getMessage());
        }
    }

    /**
     * La comanda tal como la lee quien cocina: que hay que hacer, para quien y
     * con que excepciones. Sin precios ni datos de pago, que no le sirven de
     * nada y solo estorban.
     */
    private String tarjetaDe(Orden orden) {
        StringBuilder tarjeta = new StringBuilder("🔔 **Comanda ").append(cortoDe(orden.getId())).append("**");
        tarjeta.append("  ·  ").append(etiquetaTipo(orden.getTipoOrden()));
        if (orden.getMesaNumero() != null) {
            tarjeta.append("  ·  Mesa ").append(orden.getMesaNumero());
        }
        tarjeta.append("\n");

        for (OrdenDetalle detalle : orden.getDetalles()) {
            tarjeta.append("\n**").append(detalle.getCantidad()).append("x** ")
                    .append(detalle.getPlatillo().getNombre());

            List<OrdenDetalleComplemento> complementos = detalle.getComplementos();
            if (complementos != null && !complementos.isEmpty()) {
                for (OrdenDetalleComplemento complemento : complementos) {
                    tarjeta.append("\n   ➕ ").append(complemento.getComplemento().getNombre());
                }
            }
            if (detalle.getExcepcionesNota() != null && !detalle.getExcepcionesNota().isBlank()) {
                tarjeta.append("\n   ⚠️ _").append(detalle.getExcepcionesNota()).append("_");
            }
        }
        return tarjeta.toString();
    }

    private List<BotonBot> botonesDe(UUID ordenId) {
        List<BotonBot> botones = new ArrayList<>();
        botones.add(BotonBot.primario(ACCION_TOMAR + ordenId, "▶ Tomar"));
        botones.add(BotonBot.exito(ACCION_LISTO + ordenId, "✅ Listo"));
        // Discord reparte solo los botones en filas de cinco, asi que los tiempos
        // caben en el mismo mensaje sin que el emisor tenga que saberlo.
        MINUTOS_SUGERIDOS.forEach(min ->
                botones.add(BotonBot.de(ACCION_ETA + min + ":" + ordenId, min + " min")));
        return botones;
    }

    // =====================================================================
    // Botones del cocinero
    // =====================================================================

    public void procesar(MensajeBot mensaje) {
        String remitente = mensaje.remitenteId();
        String contenido = mensaje.contenido();

        Trabajador cocinero;
        try {
            cocinero = identidad.exigirTrabajador(remitente);
        } catch (ConflictoException e) {
            emisorInterno.enviarTexto(remitente, e.getMessage());
            return;
        }

        try {
            if (contenido.startsWith(ACCION_TOMAR)) {
                tomar(remitente, cocinero, idDe(contenido, ACCION_TOMAR));
            } else if (contenido.startsWith(ACCION_LISTO)) {
                marcarListo(remitente, cocinero, idDe(contenido, ACCION_LISTO));
            } else if (contenido.startsWith(ACCION_ETA)) {
                fijarTiempo(remitente, cocinero, contenido);
            }
        } catch (Exception e) {
            log.warn("Accion de cocina '{}' de {} fallo: {}", contenido, cocinero.getUsername(), e.getMessage());
            emisorInterno.enviarTexto(remitente, "😕 No se pudo aplicar: " + e.getMessage());
        }
    }

    private void tomar(String remitente, Trabajador cocinero, UUID ordenId) {
        kdsService.tomar(ordenId);
        String aviso = "▶ **" + cortoDe(ordenId) + "** tomada por " + cocinero.getNombres() + ".";
        canalCocina().ifPresent(canal -> emisorInterno.publicarEnCanal(canal, aviso));
        emisorInterno.enviarTexto(remitente, "Listo, la comanda " + cortoDe(ordenId) + " es tuya.");
        log.info("🍳 {} tomo la comanda {} desde el bot.", cocinero.getUsername(), ordenId);
    }

    private void marcarListo(String remitente, Trabajador cocinero, UUID ordenId) {
        kdsService.marcarListo(ordenId);
        String aviso = "✅ **" + cortoDe(ordenId) + "** lista para salir.";
        canalCocina().ifPresent(canal -> emisorInterno.publicarEnCanal(canal, aviso));
        emisorInterno.enviarTexto(remitente, "Anotado: " + cortoDe(ordenId) + " lista.");

        notificarCliente(ordenId, "🍽️ ¡Tu pedido **" + cortoDe(ordenId) + "** ya está listo!");
        log.info("🍳 {} marco lista la comanda {} desde el bot.", cocinero.getUsername(), ordenId);
    }

    /**
     * El identificador del boton llega como {@code cocina:eta:<minutos>:<uuid>},
     * asi que el tiempo y la comanda viajan juntos y el bot no necesita recordar
     * sobre que comanda estaba trabajando el cocinero.
     */
    private void fijarTiempo(String remitente, Trabajador cocinero, String contenido) {
        String resto = contenido.substring(ACCION_ETA.length());
        int separador = resto.indexOf(':');
        if (separador < 0) {
            return;
        }

        int minutos = Integer.parseInt(resto.substring(0, separador));
        UUID ordenId = UUID.fromString(resto.substring(separador + 1));

        kdsService.estimarTiempo(ordenId, minutos);

        String aviso = "⏱️ **" + cortoDe(ordenId) + "** en " + minutos + " min (" + cocinero.getNombres() + ").";
        canalCocina().ifPresent(canal -> emisorInterno.publicarEnCanal(canal, aviso));
        emisorInterno.enviarTexto(remitente, "Anotado: " + cortoDe(ordenId) + " en " + minutos + " minutos.");

        notificarCliente(ordenId, "⏱️ Cocina ya tomó tu pedido **" + cortoDe(ordenId)
                + "**: estará listo en unos **" + minutos + " minutos**.");
        log.info("🍳 {} estimo {} min para la comanda {}.", cocinero.getUsername(), minutos, ordenId);
    }

    /**
     * Le cuenta al cliente lo que decidio cocina, si es que hay por donde. Una
     * comanda del salon no tiene cliente identificado y no hay nada que avisar;
     * eso no es un fallo.
     */
    private void notificarCliente(UUID ordenId, String mensaje) {
        try {
            ordenRepository.findCompletaById(ordenId)
                    .map(Orden::getCliente)
                    .map(cliente -> cliente.identificadorDeBot())
                    .ifPresent(destino -> emisorCliente.enviarTexto(destino, mensaje));
        } catch (Exception e) {
            log.warn("No se pudo avisar al cliente de la comanda {}: {}", ordenId, e.getMessage());
        }
    }

    // =====================================================================
    // Apoyo
    // =====================================================================

    private Optional<String> canalCocina() {
        return Optional.ofNullable(discord.getIfAvailable())
                .filter(DiscordProperties::tieneCanalCocina)
                .map(DiscordProperties::canalCocina);
    }

    private static UUID idDe(String contenido, String prefijo) {
        return UUID.fromString(contenido.substring(prefijo.length()));
    }

    private static String etiquetaTipo(TipoOrdenEnum tipo) {
        return switch (tipo) {
            case MESA -> "Mesa";
            case RETIRO_LOCAL -> "Para llevar";
            case DELIVERY -> "Delivery";
        };
    }

    private static String cortoDe(UUID id) {
        return id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
