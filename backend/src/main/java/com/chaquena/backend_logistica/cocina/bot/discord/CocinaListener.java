package com.chaquena.backend_logistica.cocina.bot.discord;

import com.chaquena.backend_logistica.cocina.bot.service.CocinaBotService;
import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import com.chaquena.backend_logistica.shared.mensajeria.MensajeBot;
import com.chaquena.backend_logistica.shared.mensajeria.discord.Interacciones;
import com.chaquena.backend_logistica.shared.mensajeria.discord.ListenerDeBot;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Entrada de Discord al tablero de cocina.
 *
 * <p>No declara ningun comando de barra: la conversacion la empieza siempre el
 * sistema publicando la comanda, y el cocinero solo pulsa. Es la unica de las
 * cinco entradas que funciona asi, y se nota en que es la unica que vive en un
 * canal compartido en vez de en un privado.
 */
@Component
@RequiredArgsConstructor
public class CocinaListener extends ListenerAdapter implements ListenerDeBot {

    private final CocinaBotService cocinaBotService;

    @Override
    public CanalBot canal() {
        return CanalBot.IN;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        MensajeBot mensaje = Interacciones.de(evento, CanalBot.IN);
        if (!cocinaBotService.atiende(mensaje.contenido())) {
            return;
        }
        Interacciones.acusar(evento);
        cocinaBotService.procesar(mensaje);
    }
}
