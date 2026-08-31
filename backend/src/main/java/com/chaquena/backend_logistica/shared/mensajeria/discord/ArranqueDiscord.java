package com.chaquena.backend_logistica.shared.mensajeria.discord;

import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Abre las conexiones con Discord una vez el contexto esta en pie.
 *
 * <p>Se hace en {@link ApplicationReadyEvent} y no al construir los beans por
 * dos motivos. El primero es tecnico: los listeners que hay que enganchar
 * dependen de los servicios de negocio, que a su vez dependen del adaptador de
 * salida, de modo que construir la conexion durante el cableado formaba un ciclo
 * cerrado. El segundo es de fondo: en cuanto el WebSocket queda abierto empiezan
 * a llegar mensajes, y no tiene sentido aceptarlos antes de que el POS, el
 * inventario y la base de datos esten listos para atenderlos.
 *
 * <p>Ambos bots conversan por mensaje directo, asi que necesitan el intent de
 * mensajes privados y el de contenido de mensaje. {@code MESSAGE_CONTENT} es
 * privilegiado y hay que encenderlo a mano en el portal de Discord
 * (Bot &gt; Privileged Gateway Intents &gt; Message Content Intent). Sin el, la
 * conexion se rechaza con "Disallowed intents". Es lo que sostiene los dos
 * unicos pasos de texto libre que quedan: la cantidad que teclea el almacenero y
 * la direccion que escribe el cliente.
 */
@Component
@ConditionalOnProperty(name = "app.mensajeria.proveedor", havingValue = "discord", matchIfMissing = true)
@RequiredArgsConstructor
@Order(0)
@Slf4j
public class ArranqueDiscord implements ApplicationListener<ApplicationReadyEvent> {

    private final ClientesDiscord clientes;
    private final DiscordProperties propiedades;
    private final List<ListenerDeBot> listeners;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent evento) {
        if (clientes.alguno()) {
            return;
        }

        abrir(CanalBot.IN, propiedades.botIn(), "el personal");
        abrir(CanalBot.OUT, propiedades.botOut(), "los clientes");

        if (!clientes.alguno()) {
            log.warn("⚠️ Ningun bot de Discord tiene token. Define DISCORD_BOT_IN_TOKEN y "
                    + "DISCORD_BOT_OUT_TOKEN en el .env, o cambia app.mensajeria.proveedor.");
        }
    }

    private void abrir(CanalBot canal, DiscordProperties.Bot bot, String audiencia) {
        if (!bot.configurado()) {
            log.warn("⚠️ El bot {} de Discord no tiene token: no atendera a {}.", canal, audiencia);
            return;
        }

        List<ListenerDeBot> propios = listeners.stream().filter(l -> l.canal() == canal).toList();
        List<SlashCommandData> comandos = propios.stream().flatMap(l -> l.comandos().stream()).toList();

        try {
            JDA jda = JDABuilder
                    .createLight(bot.token(), GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .setActivity(Activity.customStatus("Chaquena · " + audiencia))
                    .addEventListeners(propios.toArray())
                    .addEventListeners(new RegistradorDeComandos(canal, bot.guildId(), comandos))
                    .build();
            clientes.registrar(canal, jda);
            log.info("🤖 Bot {} de Discord conectando con {} listener(s) y {} comando(s).",
                    canal, propios.size(), comandos.size());
        } catch (Exception e) {
            // Un token invalido no puede impedir que funcionen el POS, el KDS ni
            // los reportes: el resto del sistema no depende de los bots.
            log.error("❌ No se pudo abrir el bot {} de Discord: {}", canal, e.getMessage());
        }
    }

    /**
     * Registra los comandos de barra en cuanto la conexion esta lista.
     *
     * <p>Se hace sobre el servidor concreto cuando hay {@code guild-id}: los
     * comandos de servidor aparecen al instante, mientras que los globales tardan
     * hasta una hora en propagarse por la cache de Discord.
     */
    private record RegistradorDeComandos(CanalBot canal, String guildId, List<SlashCommandData> comandos)
            implements net.dv8tion.jda.api.hooks.EventListener {

        @Override
        public void onEvent(GenericEvent evento) {
            if (!(evento instanceof ReadyEvent)) {
                return;
            }
            JDA jda = evento.getJDA();
            log.info("✅ Bot {} conectado a Discord como {}.", canal, jda.getSelfUser().getAsTag());

            if (comandos.isEmpty()) {
                return;
            }

            if (guildId != null && !guildId.isBlank()) {
                Guild guild = jda.getGuildById(guildId);
                if (guild == null) {
                    log.warn("⚠️ El bot {} no esta en el servidor {}. Los comandos se registran "
                            + "globalmente y pueden tardar en aparecer.", canal, guildId);
                } else {
                    guild.updateCommands().addCommands(comandos).queue(
                            ok -> log.info("Comandos del bot {} registrados en '{}'.", canal, guild.getName()),
                            error -> log.error("No se pudieron registrar los comandos del bot {}: {}",
                                    canal, error.getMessage()));
                    return;
                }
            }

            jda.updateCommands().addCommands(comandos).queue(
                    ok -> log.info("Comandos globales del bot {} registrados.", canal),
                    error -> log.error("No se pudieron registrar los comandos del bot {}: {}",
                            canal, error.getMessage()));
        }
    }
}
