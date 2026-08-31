package com.chaquena.backend_logistica.delivery.bot.discord;

import com.chaquena.backend_logistica.delivery.bot.service.ClienteBotService;
import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import com.chaquena.backend_logistica.shared.mensajeria.MensajeBot;
import com.chaquena.backend_logistica.shared.mensajeria.discord.Interacciones;
import com.chaquena.backend_logistica.shared.mensajeria.discord.ListenerDeBot;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Entrada de Discord al bot de clientes.
 *
 * <p>Es el unico listener del bot OUT, asi que no tiene que compartir el
 * privado con nadie: todo lo que llegue, texto o componente, va a la misma
 * maquina de estados. Por eso aqui no hace falta comprobar prefijos ni sesiones
 * abiertas, al reves que en el bot interno.
 */
@Component
@RequiredArgsConstructor
public class ClienteListener extends ListenerAdapter implements ListenerDeBot {

    private static final String COMANDO = "carta";

    private final ClienteBotService clienteBotService;

    @Override
    public CanalBot canal() {
        return CanalBot.OUT;
    }

    @Override
    public List<SlashCommandData> comandos() {
        return List.of(Commands.slash(COMANDO, "Ver la carta y hacer un pedido"));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent evento) {
        if (!COMANDO.equals(evento.getName())) {
            return;
        }
        evento.reply("🍽️ Te escribo por privado con la carta.").setEphemeral(true).queue();
        // "hola" es la palabra que reinicia la conversacion en la maquina de
        // estados; el comando de barra es solo otra forma de decirla.
        clienteBotService.procesar(new MensajeBot(CanalBot.OUT, evento.getUser().getId(),
                evento.getUser().getEffectiveName(), evento.getId(), "hola"));
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evento) {
        Interacciones.acusar(evento);
        clienteBotService.procesar(Interacciones.de(evento, CanalBot.OUT));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        Interacciones.acusar(evento);
        clienteBotService.procesar(Interacciones.de(evento, CanalBot.OUT));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent evento) {
        if (evento.getAuthor().isBot() || !evento.isFromType(ChannelType.PRIVATE)) {
            return;
        }
        MensajeBot mensaje = Interacciones.de(evento, CanalBot.OUT);
        if (mensaje.vacio()) {
            return;
        }
        clienteBotService.procesar(mensaje);
    }
}
