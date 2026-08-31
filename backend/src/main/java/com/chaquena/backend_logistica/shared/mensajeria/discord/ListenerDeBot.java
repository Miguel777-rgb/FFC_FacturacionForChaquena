package com.chaquena.backend_logistica.shared.mensajeria.discord;

import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * Marca un listener de JDA como perteneciente a uno de los dos bots y declara
 * los comandos de barra que ese listener atiende.
 *
 * <p>Existe para que el arranque no tenga que conocer los modulos de negocio:
 * {@link DiscordConfig} recoge todos los listeners que Spring encuentre, los
 * agrupa por {@link #canal()} y registra en cada aplicacion de Discord la union
 * de los comandos que declaran. Asi el listener de stock puede vivir en
 * {@code inventario/bot} y el del mozo en {@code pedidos/bot}, cada uno junto a
 * su dominio, en vez de amontonarse en un paquete de infraestructura.
 *
 * <p>Un mismo bot tiene varios listeners y JDA les entrega todos los eventos:
 * cada uno se queda solo con lo suyo mirando el prefijo del identificador del
 * componente.
 */
public interface ListenerDeBot {

    /** Bot al que se engancha este listener. */
    CanalBot canal();

    /** Comandos de barra que este listener atiende. Vacio si solo escucha botones. */
    default List<SlashCommandData> comandos() {
        return List.of();
    }
}
