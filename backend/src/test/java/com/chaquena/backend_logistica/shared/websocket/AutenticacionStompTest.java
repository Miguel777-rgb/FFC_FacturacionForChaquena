package com.chaquena.backend_logistica.shared.websocket;

import com.chaquena.backend_logistica.shared.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacionStompTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private MessageChannel canal;

    private AutenticacionStomp interceptor;

    @BeforeEach
    void preparar() {
        interceptor = new AutenticacionStomp(jwtService);
    }

    @Test
    void connectConTokenValidoDejaElUsuarioEnLaSesion() {
        when(jwtService.validateToken("bueno")).thenReturn(claims("MOZO"));

        Message<?> resultado = interceptor.preSend(trama(StompCommand.CONNECT, null, null, "Bearer bueno"), canal);

        Principal usuario = MessageHeaderAccessor.getAccessor(resultado, StompHeaderAccessor.class).getUser();
        assertThat(usuario).isInstanceOf(UsuarioStomp.class);
        UsuarioStomp mozo = (UsuarioStomp) usuario;
        assertThat(mozo.getName()).isEqualTo("rosa@chaquena.pe");
        assertThat(mozo.username()).isEqualTo("mozo1");
        assertThat(mozo.nombre()).as("solo el nombre de pila").isEqualTo("Rosa");
        assertThat(mozo.esMozo()).isTrue();
    }

    @Test
    void connectSinTokenNoAbreLaConexion() {
        assertThatThrownBy(() -> interceptor.preSend(trama(StompCommand.CONNECT, null, null, null), canal))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connectConTokenFalsoNoAbreLaConexion() {
        when(jwtService.validateToken("falso")).thenThrow(new JwtException("firma invalida"));

        assertThatThrownBy(() -> interceptor.preSend(trama(StompCommand.CONNECT, null, null, "Bearer falso"), canal))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void elMozoSeSuscribeASuTemaYCocinaNo() {
        assertThatCode(() -> interceptor.preSend(
                trama(StompCommand.SUBSCRIBE, "/topic/llamados/mozos", usuario("MOZO"), null), canal))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preSend(
                trama(StompCommand.SUBSCRIBE, "/topic/llamados/mozos", usuario("COCINA"), null), canal))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void elAdministradorEscuchaLoMismoQueCocina() {
        assertThatCode(() -> interceptor.preSend(
                trama(StompCommand.SUBSCRIBE, "/topic/llamados/cocina", usuario("ADMIN"), null), canal))
                .doesNotThrowAnyException();
    }

    @Test
    void soloCocinaLlamaYSoloElMozoAtiende() {
        assertThatCode(() -> interceptor.preSend(
                trama(StompCommand.SEND, "/app/llamados/llamar", usuario("COCINA"), null), canal))
                .doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preSend(
                trama(StompCommand.SEND, "/app/llamados/atender", usuario("MOZO"), null), canal))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> interceptor.preSend(
                trama(StompCommand.SEND, "/app/llamados/llamar", usuario("MOZO"), null), canal))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                trama(StompCommand.SEND, "/app/llamados/atender", usuario("COCINA"), null), canal))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void unDestinoQueNoEstaEnLaTablaSeRechazaAunqueSeasAdmin() {
        assertThatThrownBy(() -> interceptor.preSend(
                trama(StompCommand.SUBSCRIBE, "/topic/otra-cosa", usuario("ADMIN"), null), canal))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void unaTramaSinUsuarioAutenticadoSeRechaza() {
        assertThatThrownBy(() -> interceptor.preSend(
                trama(StompCommand.SUBSCRIBE, "/topic/llamados/mozos", null, null), canal))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void unaConexionAbiertaNoAlargaUnTokenVencido() {
        UsuarioStomp vencido = new UsuarioStomp("rosa@chaquena.pe", "mozo1", "Rosa",
                Set.of("ROLE_MOZO"), Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> interceptor.preSend(
                trama(StompCommand.SEND, "/app/llamados/atender", vencido, null), canal))
                .isInstanceOf(MessageDeliveryException.class);
    }

    // -------------------------------------------------------------------------

    private static Message<byte[]> trama(StompCommand comando, String destino, Principal usuario,
            String autorizacion) {
        StompHeaderAccessor acceso = StompHeaderAccessor.create(comando);
        if (destino != null) acceso.setDestination(destino);
        if (usuario != null) acceso.setUser(usuario);
        if (autorizacion != null) acceso.addNativeHeader("Authorization", autorizacion);
        // Como en la cadena real: el interceptor recibe un acceso modificable.
        acceso.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acceso.getMessageHeaders());
    }

    private static UsuarioStomp usuario(String rol) {
        return new UsuarioStomp(rol.toLowerCase() + "@chaquena.pe", rol.toLowerCase(), rol,
                Set.of("ROLE_" + rol), Instant.now().plus(1, ChronoUnit.HOURS));
    }

    private static Claims claims(String... roles) {
        return Jwts.claims()
                .subject("rosa@chaquena.pe")
                .add("username", "mozo1")
                .add("nombres", "Rosa Maria")
                .add("roles", List.of(roles))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .build();
    }
}
