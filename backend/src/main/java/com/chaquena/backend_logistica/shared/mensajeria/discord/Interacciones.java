package com.chaquena.backend_logistica.shared.mensajeria.discord;

import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import com.chaquena.backend_logistica.shared.mensajeria.MensajeBot;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;

/**
 * Traduce los eventos de JDA al {@link MensajeBot} neutro y acusa las
 * interacciones.
 *
 * <p>El acuse importa mas de lo que parece. Discord da tres segundos para
 * responder una interaccion; si nadie lo hace, al usuario le aparece
 * "la aplicacion no respondio" aunque el backend haya hecho su trabajo. Como
 * aqui la respuesta de verdad la manda el servicio por su cuenta, y no como
 * contestacion a la interaccion, hay que acusarla aparte y en silencio con
 * {@code deferEdit}, que no toca el mensaje original ni añade uno nuevo.
 */
public final class Interacciones {

    private Interacciones() {
    }

    /** Boton pulsado: el contenido es el identificador del boton. */
    public static MensajeBot de(ButtonInteractionEvent evento, CanalBot canal) {
        return construir(canal, evento.getUser(), evento.getMessageId(), evento.getComponentId());
    }

    /** Fila elegida de una lista: el contenido es el identificador de la opcion. */
    public static MensajeBot de(StringSelectInteractionEvent evento, CanalBot canal) {
        String valor = evento.getValues().isEmpty() ? "" : evento.getValues().get(0);
        return construir(canal, evento.getUser(), evento.getMessageId(), valor);
    }

    /** Comando de barra: el contenido es el nombre del comando. */
    public static MensajeBot de(SlashCommandInteractionEvent evento, CanalBot canal) {
        return construir(canal, evento.getUser(), evento.getId(), evento.getName());
    }

    /** Mensaje escrito a mano en el privado del bot. */
    public static MensajeBot de(MessageReceivedEvent evento, CanalBot canal) {
        return construir(canal, evento.getAuthor(), evento.getMessageId(),
                evento.getMessage().getContentRaw());
    }

    /** Acuse silencioso de una interaccion de componente. */
    public static void acusar(IMessageEditCallback interaccion) {
        interaccion.deferEdit().queue(ok -> {
        }, error -> {
            // La interaccion pudo caducar mientras el backend trabajaba; el
            // mensaje de respuesta sale igual, asi que no hay nada que hacer.
        });
    }

    private static MensajeBot construir(CanalBot canal, User autor, String messageId, String contenido) {
        return new MensajeBot(canal, autor.getId(), autor.getEffectiveName(), messageId, contenido);
    }
}
