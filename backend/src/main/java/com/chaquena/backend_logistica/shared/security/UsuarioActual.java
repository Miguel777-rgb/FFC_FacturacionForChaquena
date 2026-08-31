package com.chaquena.backend_logistica.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Acceso al usuario autenticado para los campos de auditoria. Antes de esto
 * todas las entidades quedaban con created_by = "SYSTEM" y la auditoria
 * obligatoria del diseno no auditaba nada.
 */
public final class UsuarioActual {

    public static final String SISTEMA = "SYSTEM";

    private UsuarioActual() {
    }

    /**
     * Correo o username del trabajador autenticado, o "SYSTEM" cuando la
     * operacion la dispara un proceso de fondo (worker de outbox, bots de
     * mensajeria, seeder de arranque).
     */
    public static String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return SISTEMA;
        }
        if ("anonymousUser".equals(auth.getName())) {
            return SISTEMA;
        }
        return auth.getName();
    }
}
