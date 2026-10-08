package com.chaquena.backend_logistica.shared.websocket;

import com.chaquena.backend_logistica.shared.security.AutoridadesDelToken;
import com.chaquena.backend_logistica.shared.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Autentica y autoriza cada trama que llega por el WebSocket.
 *
 * <p>El navegador no deja poner la cabecera {@code Authorization} al abrir un
 * WebSocket, asi que el saludo HTTP entra sin token y el JWT viaja dentro, en
 * la cabecera de la trama STOMP {@code CONNECT}. Se valida con el mismo
 * {@link JwtService} y las mismas autoridades que el filtro HTTP. Una conexion
 * sin token valido no llega a abrirse: el cliente recibe una trama ERROR.
 *
 * <p>No se usa la seguridad de mensajes de Spring Security: exige un token
 * CSRF en el CONNECT, y una API sin sesiones no tiene de donde sacarlo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutenticacionStomp implements ChannelInterceptor {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
        StompHeaderAccessor acceso = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
        if (acceso == null || acceso.getCommand() == null) {
            return mensaje;
        }
        StompCommand comando = acceso.getCommand();
        if (comando == StompCommand.CONNECT) {
            acceso.setUser(autenticar(acceso));
        } else if (comando == StompCommand.SUBSCRIBE) {
            exigir(PermisosStomp.puedeSuscribirse(usuario(acceso), acceso.getDestination()), acceso);
        } else if (comando == StompCommand.SEND) {
            exigir(PermisosStomp.puedeEnviar(usuario(acceso), acceso.getDestination()), acceso);
        }
        return mensaje;
    }

    private UsuarioStomp autenticar(StompHeaderAccessor acceso) {
        String cabecera = acceso.getFirstNativeHeader("Authorization");
        if (cabecera == null || !cabecera.startsWith(PREFIJO)) {
            log.warn("CONNECT rechazado: sin token (sesion {}).", acceso.getSessionId());
            throw new MessageDeliveryException("Falta el token en la trama CONNECT.");
        }
        try {
            Claims claims = jwtService.validateToken(cabecera.substring(PREFIJO.length()).trim());
            return new UsuarioStomp(
                    claims.getSubject(),
                    claims.get("username", String.class),
                    nombreVisible(claims),
                    AutoridadesDelToken.de(claims).stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toUnmodifiableSet()),
                    claims.getExpiration() == null ? null : claims.getExpiration().toInstant());
        } catch (RuntimeException e) {
            log.warn("CONNECT rechazado: {}", e.getMessage());
            throw new MessageDeliveryException("Token invalido o vencido.");
        }
    }

    /**
     * El usuario lo fija Spring en la sesion despues del CONNECT. Si no es uno
     * nuestro, la trama no paso por {@link #autenticar} y no se le deja seguir.
     */
    private UsuarioStomp usuario(StompHeaderAccessor acceso) {
        Principal principal = acceso.getUser();
        if (principal instanceof UsuarioStomp usuario && !usuario.vencido(Instant.now())) {
            return usuario;
        }
        log.warn("{} rechazado en {}: la sesion no se autentico o su token vencio.",
                acceso.getCommand(), acceso.getDestination());
        throw new MessageDeliveryException("La sesion del WebSocket no es valida o vencio.");
    }

    private void exigir(boolean permitido, StompHeaderAccessor acceso) {
        if (!permitido) {
            log.warn("{} rechazado en {} para {}.", acceso.getCommand(), acceso.getDestination(),
                    acceso.getUser() == null ? "una sesion sin usuario" : acceso.getUser().getName());
            throw new MessageDeliveryException(
                    acceso.getCommand() + " no permitido en " + acceso.getDestination() + ".");
        }
    }

    /** El nombre de pila, que es lo que se lee en «Rosa va en camino». */
    private static String nombreVisible(Claims claims) {
        String nombres = claims.get("nombres", String.class);
        if (nombres != null && !nombres.isBlank()) {
            return nombres.trim().split("\\s+")[0];
        }
        String username = claims.get("username", String.class);
        return username != null ? username : claims.getSubject();
    }
}
