package com.chaquena.backend_logistica.shared.websocket;

import java.util.Map;
import java.util.Set;

/**
 * Que puede hacer cada rol por el WebSocket.
 *
 * <p>Los mensajes STOMP no pasan por los {@code @PreAuthorize} de los
 * controladores HTTP, asi que la regla vive aqui y se aplica en el interceptor.
 * La tabla es una lista blanca: un destino que no figura se rechaza, de modo
 * que un canal nuevo nace cerrado hasta que alguien decide quien lo usa.
 */
final class PermisosStomp {

    private static final Set<String> SALON_Y_COCINA = Set.of("MOZO", "COCINA", "ADMIN");

    private static final Map<String, Set<String>> SUSCRIPCIONES = Map.of(
            "/topic/llamados/mozos", Set.of("MOZO"),
            "/topic/llamados/cocina", Set.of("COCINA", "ADMIN"),
            "/user/queue/llamados", SALON_Y_COCINA,
            "/app/llamados/estado", SALON_Y_COCINA);

    private static final Map<String, Set<String>> ENVIOS = Map.of(
            "/app/llamados/llamar", Set.of("COCINA", "ADMIN"),
            "/app/llamados/atender", Set.of("MOZO"));

    private PermisosStomp() {
    }

    static boolean puedeSuscribirse(UsuarioStomp usuario, String destino) {
        return permite(SUSCRIPCIONES, usuario, destino);
    }

    static boolean puedeEnviar(UsuarioStomp usuario, String destino) {
        return permite(ENVIOS, usuario, destino);
    }

    private static boolean permite(Map<String, Set<String>> tabla, UsuarioStomp usuario, String destino) {
        Set<String> roles = destino == null ? null : tabla.get(destino);
        return roles != null && usuario.tieneAlgunRol(roles.toArray(String[]::new));
    }
}
