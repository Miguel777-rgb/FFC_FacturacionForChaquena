package com.chaquena.backend_logistica.shared.websocket;

import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

/**
 * Quien esta al otro lado de una conexion STOMP, tal como lo dijo su JWT en la
 * trama CONNECT.
 *
 * <p>El nombre de principal es el correo, el mismo subject que usa el filtro
 * HTTP: los destinos {@code /user/...} se resuelven por ese nombre. El username
 * y el nombre visible viajan aparte porque son los que se guardan y se ensenan.
 *
 * @param autoridades las del token, normalizadas igual que en HTTP ("ROLE_MOZO")
 * @param expiraEn    cuando vence el token; una conexion abierta no lo alarga
 */
public record UsuarioStomp(String correo, String username, String nombre,
        Set<String> autoridades, Instant expiraEn) implements Principal {

    @Override
    public String getName() {
        return correo;
    }

    public boolean tieneAlgunRol(String... roles) {
        return Arrays.stream(roles).anyMatch(rol -> autoridades.contains("ROLE_" + rol));
    }

    public boolean esMozo() {
        return tieneAlgunRol("MOZO");
    }

    public boolean vencido(Instant ahora) {
        return expiraEn != null && !expiraEn.isAfter(ahora);
    }
}
