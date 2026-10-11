package com.chaquena.backend_logistica.shared.mensajeria.discord;

import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * Asi cada listener vive junto a su dominio: {@link ArranqueDiscord} los agrupa por {@link #canal()} y
 * registra la union de sus comandos. Cada uno se queda con lo suyo por el prefijo del identificador.
 */
public interface ListenerDeBot {

    /** Bot al que se engancha este listener. */
    CanalBot canal();

    /** Comandos de barra que este listener atiende. Vacio si solo escucha botones. */
    default List<SlashCommandData> comandos() {
        return List.of();
    }
}
