package com.chaquena.backend_logistica.shared.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Quien tiene el WebSocket abierto, lo que el SSE no puede saber. Cuenta mozos, no pestanas, y vive en
 * memoria como el difusor del SSE: una sola instancia.
 */
@Component
@RequiredArgsConstructor
public class PresenciaWebSocket {

    private final ApplicationEventPublisher eventos;

    private final Map<String, UsuarioStomp> sesiones = new ConcurrentHashMap<>();
    private final AtomicInteger ultimoConteo = new AtomicInteger();

    @EventListener
    public void alConectarse(SessionConnectedEvent evento) {
        String sesion = SimpMessageHeaderAccessor.getSessionId(evento.getMessage().getHeaders());
        if (sesion != null && evento.getUser() instanceof UsuarioStomp usuario) {
            sesiones.put(sesion, usuario);
            avisarSiCambio();
        }
    }

    @EventListener
    public void alDesconectarse(SessionDisconnectEvent evento) {
        if (sesiones.remove(evento.getSessionId()) != null) {
            avisarSiCambio();
        }
    }

    public int mozosConectados() {
        return (int) sesiones.values().stream()
                .filter(UsuarioStomp::esMozo)
                .map(UsuarioStomp::username)
                .distinct()
                .count();
    }

    private void avisarSiCambio() {
        int ahora = mozosConectados();
        if (ultimoConteo.getAndSet(ahora) != ahora) {
            eventos.publishEvent(new PresenciaCambiadaEvent(ahora));
        }
    }
}
