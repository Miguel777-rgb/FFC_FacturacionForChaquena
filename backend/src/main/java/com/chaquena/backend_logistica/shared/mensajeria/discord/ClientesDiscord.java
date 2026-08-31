package com.chaquena.backend_logistica.shared.mensajeria.discord;

import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Las conexiones vivas con Discord, una por bot.
 *
 * <p>A diferencia de la Cloud API de Meta, que era una llamada HTTP suelta por
 * mensaje, Discord mantiene un WebSocket abierto contra la pasarela. Eso es lo
 * que permite prescindir del tunel publico: el backend abre la conexion hacia
 * afuera y recibe por ella, asi que la demostracion corre desde localhost sin
 * cloudflared ni webhook expuesto.
 *
 * <p>Un bot sin token no es un error de arranque: se queda fuera del mapa y sus
 * envios se registran en el log sin salir.
 */
@Slf4j
public class ClientesDiscord implements AutoCloseable {

    private final Map<CanalBot, JDA> porCanal = new EnumMap<>(CanalBot.class);

    public void registrar(CanalBot canal, JDA jda) {
        porCanal.put(canal, jda);
    }

    public Optional<JDA> de(CanalBot canal) {
        return Optional.ofNullable(porCanal.get(canal));
    }

    public boolean alguno() {
        return !porCanal.isEmpty();
    }

    @Override
    public void close() {
        porCanal.forEach((canal, jda) -> {
            log.info("Cerrando la conexion del bot {} con Discord.", canal);
            jda.shutdown();
        });
        porCanal.clear();
    }
}
