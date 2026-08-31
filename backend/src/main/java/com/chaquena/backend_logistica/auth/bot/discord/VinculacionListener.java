package com.chaquena.backend_logistica.auth.bot.discord;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.service.IdentidadBotService;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import com.chaquena.backend_logistica.shared.mensajeria.discord.ListenerDeBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Puerta de entrada del bot interno: atar la cuenta de Discord a un trabajador.
 *
 * <p>Todo lo demas que hace el personal por el bot —stock, comandas, cocina—
 * exige haber pasado por aqui una vez. Se responde en efimero para que el correo
 * del trabajador no quede escrito en un canal a la vista de todos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VinculacionListener extends ListenerAdapter implements ListenerDeBot {

    private static final String COMANDO_VINCULAR = "vincular";
    private static final String COMANDO_QUIEN_SOY = "quiensoy";
    private static final String OPCION_CORREO = "correo";

    private final IdentidadBotService identidad;

    @Override
    public CanalBot canal() {
        return CanalBot.IN;
    }

    @Override
    public List<SlashCommandData> comandos() {
        return List.of(
                Commands.slash(COMANDO_VINCULAR,
                                "Ata tu cuenta de Discord a tu ficha de trabajador")
                        .addOption(OptionType.STRING, OPCION_CORREO,
                                "El correo con el que estas dado de alta", true),
                Commands.slash(COMANDO_QUIEN_SOY, "Muestra a que trabajador esta atada tu cuenta"));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent evento) {
        switch (evento.getName()) {
            case COMANDO_VINCULAR -> vincular(evento);
            case COMANDO_QUIEN_SOY -> quienSoy(evento);
            default -> {
                // De otro listener del mismo bot.
            }
        }
    }

    private void vincular(SlashCommandInteractionEvent evento) {
        String correo = evento.getOption(OPCION_CORREO, "", option -> option.getAsString());

        try {
            Trabajador trabajador = identidad.vincular(
                    evento.getUser().getId(), evento.getUser().getName(), correo);

            evento.reply("✅ Listo, **" + trabajador.getNombres() + " " + trabajador.getApellidos()
                            + "**. Ya puedes usar `/stock` y `/pedido`.")
                    .setEphemeral(true).queue();
        } catch (ConflictoException e) {
            evento.reply("⛔ " + e.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            log.error("Error vinculando la cuenta {}: {}", evento.getUser().getId(), e.getMessage(), e);
            evento.reply("😕 No se pudo completar la vinculación. Avisa al administrador.")
                    .setEphemeral(true).queue();
        }
    }

    private void quienSoy(SlashCommandInteractionEvent evento) {
        Optional<Trabajador> trabajador = identidad.trabajadorDe(evento.getUser().getId());

        String respuesta = trabajador
                .map(t -> "👤 Eres **" + t.getNombres() + " " + t.getApellidos() + "**"
                        + (t.getCargo() != null ? " · " + t.getCargo().getNombre() : "")
                        + "\nUsuario: `" + t.getUsername() + "`")
                .orElse("🔓 Tu cuenta no está vinculada todavía.\n"
                        + "Usa `/vincular correo:tu.correo@gmail.com`.");

        evento.reply(respuesta).setEphemeral(true).queue();
    }
}
