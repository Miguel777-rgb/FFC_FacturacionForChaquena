package com.chaquena.backend_logistica.shared.mensajeria.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de las dos aplicaciones de Discord del negocio.
 *
 * <p>Cada bot es una aplicacion distinta en el portal de desarrolladores, con
 * su propio token, igual que antes cada bot era un numero distinto dado de alta
 * en Meta. Lo que Discord añade y WhatsApp no tenia es el servidor (guild) y
 * sus canales, que es donde vive el tablero de cocina.
 *
 * @param botIn        aplicacion interna del personal
 * @param botOut       aplicacion de cara al cliente
 * @param canalCocina  id del canal donde se publican las comandas para cocina
 */
@ConfigurationProperties(prefix = "discord")
public record DiscordProperties(Bot botIn, Bot botOut, String canalCocina) {

    /**
     * @param token   token del bot (portal de Discord > Bot > Reset Token)
     * @param guildId servidor donde registrar los comandos. Con guild los
     *                comandos aparecen al instante; sin el se registran
     *                globalmente y Discord tarda hasta una hora en propagarlos,
     *                lo que en una demostracion se siente como que no funciona.
     */
    public record Bot(String token, String guildId) {

        public boolean configurado() {
            return token != null && !token.isBlank();
        }
    }

    public DiscordProperties {
        botIn = botIn == null ? new Bot(null, null) : botIn;
        botOut = botOut == null ? new Bot(null, null) : botOut;
    }

    public boolean tieneCanalCocina() {
        return canalCocina != null && !canalCocina.isBlank();
    }
}
