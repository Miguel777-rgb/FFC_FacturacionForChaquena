package com.chaquena.backend_logistica.shared.mensajeria.discord;

import com.chaquena.backend_logistica.shared.mensajeria.BotonBot;
import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import com.chaquena.backend_logistica.shared.mensajeria.DestinoBot;
import com.chaquena.backend_logistica.shared.mensajeria.MensajeriaPort;
import com.chaquena.backend_logistica.shared.mensajeria.OpcionBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Adaptador de Discord. Traduce el vocabulario del puerto —decir algo, ofrecer
 * una lista cerrada, ofrecer botones— a los componentes de mensaje de Discord.
 *
 * <p>Las equivalencias no son exactas y ahi esta el interes del ejercicio:
 *
 * <ul>
 *   <li>La lista desplegable de WhatsApp se convierte en un <em>select menu</em>
 *       que admite 25 filas en vez de 10, asi que la carta cabe entera mucho mas
 *       a menudo y el paso intermedio de elegir categoria se dispara menos.</li>
 *   <li>Los botones de respuesta rapida pasan de 3 a 5 por fila y hasta 25 por
 *       mensaje, de modo que los tiempos de cocina caben en una sola pregunta.</li>
 *   <li>Discord tiene canales; WhatsApp no. El tablero de cocina se apoya en eso
 *       y por eso el destino se modela con un tipo y no con un telefono.</li>
 * </ul>
 *
 * <p>Ningun fallo de envio se propaga: se registra y la conversacion sigue.
 */
@Component
@ConditionalOnProperty(name = "app.mensajeria.proveedor", havingValue = "discord", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DiscordMensajeriaAdapter implements MensajeriaPort {

    /** Tope de filas de un select menu de Discord. */
    private static final int MAX_OPCIONES = 25;
    /** Tope de botones por fila. Con mas de cinco hay que repartirlos en varias. */
    private static final int MAX_BOTONES_POR_FILA = 5;
    private static final int MAX_FILAS_BOTONES = 5;
    private static final int MAX_ETIQUETA = 80;
    private static final int MAX_DESCRIPCION = 100;
    private static final int MAX_TEXTO = 2000;

    /**
     * Identificador del select menu. Lo que el listener necesita no es este id
     * sino el valor elegido, que es el id de la {@link OpcionBot} y ya lleva el
     * prefijo del flujo al que pertenece.
     */
    public static final String ID_SELECTOR = "bot:seleccion";

    private final ClientesDiscord clientes;

    @Override
    public String nombre() {
        return "discord";
    }

    @Override
    public int maxOpciones() {
        return MAX_OPCIONES;
    }

    @Override
    public boolean disponible() {
        return clientes.alguno();
    }

    @Override
    public void enviarTexto(CanalBot bot, DestinoBot destino, String texto) {
        enviar(bot, destino, "Texto", canal -> canal.sendMessage(recortar(texto, MAX_TEXTO)));
    }

    @Override
    public void enviarOpciones(CanalBot bot, DestinoBot destino, String titulo, String cuerpo,
            String textoBoton, List<OpcionBot> opciones) {

        if (opciones.isEmpty()) {
            log.warn("[{}] Lista '{}' sin opciones: no se envio nada a {}.", bot, titulo, destino.id());
            return;
        }
        if (opciones.size() > MAX_OPCIONES) {
            log.warn("[{}] La lista '{}' traia {} opciones y Discord solo admite {}. Se enviaron las primeras.",
                    bot, titulo, opciones.size(), MAX_OPCIONES);
        }

        StringSelectMenu.Builder menu = StringSelectMenu.create(ID_SELECTOR)
                .setPlaceholder(recortar(textoBoton, MAX_ETIQUETA))
                .setRequiredRange(1, 1);

        for (OpcionBot opcion : opciones.stream().limit(MAX_OPCIONES).toList()) {
            if (opcion.descripcion() == null || opcion.descripcion().isBlank()) {
                menu.addOption(recortar(opcion.titulo(), MAX_ETIQUETA), opcion.id());
            } else {
                menu.addOption(recortar(opcion.titulo(), MAX_ETIQUETA), opcion.id(),
                        recortar(opcion.descripcion(), MAX_DESCRIPCION));
            }
        }

        String encabezado = "**" + titulo + "**\n" + cuerpo;
        enviar(bot, destino, "Lista", canal -> canal
                .sendMessage(recortar(encabezado, MAX_TEXTO))
                .addComponents(ActionRow.of(menu.build())));
    }

    @Override
    public void enviarBotones(CanalBot bot, DestinoBot destino, String cuerpo, List<BotonBot> botones) {
        if (botones.isEmpty()) {
            enviarTexto(bot, destino, cuerpo);
            return;
        }

        List<MessageTopLevelComponent> filas = repartirEnFilas(botones);
        enviar(bot, destino, "Botones", canal -> canal
                .sendMessage(recortar(cuerpo, MAX_TEXTO))
                .addComponents(filas));
    }

    // ---------------- Apoyo ----------------

    /**
     * Discord admite cinco botones por fila y cinco filas. Repartirlos aqui
     * evita que el emisor tenga que saberlo: pide los botones que necesita y el
     * adaptador los acomoda.
     */
    private List<MessageTopLevelComponent> repartirEnFilas(List<BotonBot> botones) {
        int tope = MAX_BOTONES_POR_FILA * MAX_FILAS_BOTONES;
        if (botones.size() > tope) {
            log.warn("Se pidieron {} botones y Discord admite {}. Se enviaron los primeros.",
                    botones.size(), tope);
        }

        List<Button> traducidos = botones.stream().limit(tope).map(this::traducir).toList();
        List<MessageTopLevelComponent> filas = new ArrayList<>();
        for (int desde = 0; desde < traducidos.size(); desde += MAX_BOTONES_POR_FILA) {
            filas.add(ActionRow.of(traducidos.subList(desde,
                    Math.min(desde + MAX_BOTONES_POR_FILA, traducidos.size()))));
        }
        return filas;
    }

    private Button traducir(BotonBot boton) {
        String etiqueta = recortar(boton.etiqueta(), MAX_ETIQUETA);
        return switch (boton.estilo()) {
            case PRIMARIO -> Button.primary(boton.id(), etiqueta);
            case EXITO -> Button.success(boton.id(), etiqueta);
            case PELIGRO -> Button.danger(boton.id(), etiqueta);
            case SECUNDARIO -> Button.secondary(boton.id(), etiqueta);
        };
    }

    /**
     * Resuelve el destino y encola el envio. Un usuario exige abrir antes el
     * canal privado, que es una llamada REST propia; un canal ya esta en la
     * cache de la pasarela.
     */
    private void enviar(CanalBot bot, DestinoBot destino, String tipo,
            Function<MessageChannel, MessageCreateAction> accion) {

        Optional<JDA> conexion = clientes.de(bot);
        if (conexion.isEmpty()) {
            log.warn("[{}] Sin conexion con Discord: no se envio [{}] a {}.", bot, tipo, destino.id());
            return;
        }
        JDA jda = conexion.get();

        try {
            if (destino.esCanal()) {
                MessageChannel canal = jda.getChannelById(MessageChannel.class, destino.id());
                if (canal == null) {
                    log.warn("[{}] El canal {} no existe o el bot no lo ve: no se envio [{}].",
                            bot, destino.id(), tipo);
                    return;
                }
                encolar(bot, destino, tipo, accion.apply(canal));
                return;
            }

            jda.openPrivateChannelById(destino.id()).queue(
                    privado -> encolar(bot, destino, tipo, accion.apply(privado)),
                    // Ocurre cuando la persona tiene los privados cerrados o no
                    // comparte ningun servidor con el bot; no es un fallo nuestro.
                    error -> log.warn("[{}] No se pudo abrir el privado con {}: {}",
                            bot, destino.id(), error.getMessage()));
        } catch (Exception e) {
            log.error("[{}] Error enviando [{}] a {}: {}", bot, tipo, destino.id(), e.getMessage());
        }
    }

    private void encolar(CanalBot bot, DestinoBot destino, String tipo, MessageCreateAction envio) {
        envio.queue(
                ok -> log.debug("[{}] [{}] entregado a {}.", bot, tipo, destino.id()),
                error -> log.warn("[{}] Discord rechazo [{}] hacia {}: {}",
                        bot, tipo, destino.id(), error.getMessage()));
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
