package com.chaquena.backend_logistica.pedidos.bot.discord;

import com.chaquena.backend_logistica.pedidos.bot.service.MozoBotService;
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

/** Entrada de Discord a la comanda que levanta el mozo. */
@Component
@RequiredArgsConstructor
public class MozoListener extends ListenerAdapter implements ListenerDeBot {

    private static final String COMANDO = "pedido";

    private final MozoBotService mozoBotService;

    @Override
    public CanalBot canal() {
        return CanalBot.IN;
    }

    @Override
    public List<SlashCommandData> comandos() {
        return List.of(Commands.slash(COMANDO, "Levantar una comanda y mandarla a cocina"));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent evento) {
        if (!COMANDO.equals(evento.getName())) {
            return;
        }
        evento.reply("🧾 Te escribo por privado para armar la comanda.").setEphemeral(true).queue();
        mozoBotService.iniciar(Interacciones.de(evento, CanalBot.IN));
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evento) {
        MensajeBot mensaje = Interacciones.de(evento, CanalBot.IN);
        if (!mozoBotService.atiende(mensaje.contenido())) {
            return;
        }
        Interacciones.acusar(evento);
        mozoBotService.procesar(mensaje);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        MensajeBot mensaje = Interacciones.de(evento, CanalBot.IN);
        if (!mozoBotService.atiende(mensaje.contenido())) {
            return;
        }
        Interacciones.acusar(evento);
        mozoBotService.procesar(mensaje);
    }

    /** La nota del platillo, unico texto libre de este flujo. */
    @Override
    public void onMessageReceived(MessageReceivedEvent evento) {
        if (evento.getAuthor().isBot() || !evento.isFromType(ChannelType.PRIVATE)) {
            return;
        }
        MensajeBot mensaje = Interacciones.de(evento, CanalBot.IN);
        if (mensaje.vacio() || !mozoBotService.tieneSesionAbierta(mensaje.remitenteId())) {
            return;
        }
        mozoBotService.procesar(mensaje);
    }
}
