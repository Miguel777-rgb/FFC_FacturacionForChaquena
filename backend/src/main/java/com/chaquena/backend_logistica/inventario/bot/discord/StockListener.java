package com.chaquena.backend_logistica.inventario.bot.discord;

import com.chaquena.backend_logistica.inventario.bot.service.StockBotService;
import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import com.chaquena.backend_logistica.shared.mensajeria.MensajeBot;
import com.chaquena.backend_logistica.shared.mensajeria.discord.Interacciones;
import com.chaquena.backend_logistica.shared.mensajeria.discord.ListenerDeBot;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Entrada de Discord al control de stock.
 *
 * <p>El listener no decide nada de negocio: traduce el evento y se lo pasa a
 * {@link StockBotService}, que es el mismo servicio que atenderia si el
 * proveedor volviera a ser WhatsApp. Lo unico suyo es saber que eventos le
 * tocan, y eso lo resuelve por el prefijo de los identificadores.
 */
@Component
@RequiredArgsConstructor
public class StockListener extends ListenerAdapter implements ListenerDeBot {

    private static final String COMANDO = "stock";

    private final StockBotService stockBotService;

    @Override
    public CanalBot canal() {
        return CanalBot.IN;
    }

    @Override
    public List<SlashCommandData> comandos() {
        return List.of(Commands.slash(COMANDO, "Registrar cocinado, merma o compra de un insumo"));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent evento) {
        if (!COMANDO.equals(evento.getName())) {
            return;
        }
        evento.reply("📦 Te escribo por privado para armar el registro.").setEphemeral(true).queue();
        stockBotService.iniciar(Interacciones.de(evento, CanalBot.IN));
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evento) {
        MensajeBot mensaje = Interacciones.de(evento, CanalBot.IN);
        if (!stockBotService.atiende(mensaje.contenido())) {
            return;
        }
        Interacciones.acusar(evento);
        stockBotService.procesar(mensaje);
    }

    /**
     * La cantidad se teclea a mano y llega sin prefijo, asi que solo se atiende
     * si hay una conversacion de stock abierta con esa persona. Sin esa
     * comprobacion, la nota de un platillo que el mozo escribe en el mismo
     * privado acabaria interpretandose como una cantidad de insumo.
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent evento) {
        if (evento.getAuthor().isBot() || !evento.isFromType(ChannelType.PRIVATE)) {
            return;
        }
        MensajeBot mensaje = Interacciones.de(evento, CanalBot.IN);
        if (mensaje.vacio() || !stockBotService.tieneSesionAbierta(mensaje.remitenteId())) {
            return;
        }
        stockBotService.procesar(mensaje);
    }
}
