package com.chaquena.backend_logistica.shared.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PresenciaWebSocketTest {

    @Mock
    private ApplicationEventPublisher eventos;

    private PresenciaWebSocket presencia;

    @BeforeEach
    void preparar() {
        presencia = new PresenciaWebSocket(eventos);
    }

    @Test
    void unMozoConDosPestanasEsUnMozo() {
        presencia.alConectarse(conectado("s1", usuario("mozo1", "MOZO")));
        presencia.alConectarse(conectado("s2", usuario("mozo1", "MOZO")));

        assertThat(presencia.mozosConectados()).isEqualTo(1);
    }

    @Test
    void cocinaConectadaNoCuentaComoMozo() {
        presencia.alConectarse(conectado("s1", usuario("chef1", "COCINA")));

        assertThat(presencia.mozosConectados()).isZero();
    }

    @Test
    void elMozoDejaDeContarCuandoCierraSuUltimaPestana() {
        presencia.alConectarse(conectado("s1", usuario("mozo1", "MOZO")));
        presencia.alConectarse(conectado("s2", usuario("mozo1", "MOZO")));

        presencia.alDesconectarse(desconectado("s1"));
        assertThat(presencia.mozosConectados()).isEqualTo(1);

        presencia.alDesconectarse(desconectado("s2"));
        assertThat(presencia.mozosConectados()).isZero();
    }

    @Test
    void soloSeAvisaACocinaCuandoCambiaElNumero() {
        presencia.alConectarse(conectado("s1", usuario("mozo1", "MOZO")));   // 0 -> 1
        presencia.alConectarse(conectado("s2", usuario("mozo1", "MOZO")));   // sigue en 1
        presencia.alConectarse(conectado("s3", usuario("chef1", "COCINA"))); // sigue en 1
        presencia.alConectarse(conectado("s4", usuario("mozo2", "MOZO")));   // 1 -> 2

        verify(eventos, times(2)).publishEvent(any(PresenciaCambiadaEvent.class));
    }

    // -------------------------------------------------------------------------

    private static UsuarioStomp usuario(String username, String rol) {
        return new UsuarioStomp(username + "@chaquena.pe", username, username, Set.of("ROLE_" + rol),
                Instant.now().plus(1, ChronoUnit.HOURS));
    }

    private static Message<byte[]> mensaje(String sesion) {
        SimpMessageHeaderAccessor acceso = SimpMessageHeaderAccessor.create();
        acceso.setSessionId(sesion);
        return MessageBuilder.createMessage(new byte[0], acceso.getMessageHeaders());
    }

    private static SessionConnectedEvent conectado(String sesion, UsuarioStomp usuario) {
        return new SessionConnectedEvent(new Object(), mensaje(sesion), usuario);
    }

    private static SessionDisconnectEvent desconectado(String sesion) {
        return new SessionDisconnectEvent(new Object(), mensaje(sesion), sesion, CloseStatus.NORMAL);
    }
}
