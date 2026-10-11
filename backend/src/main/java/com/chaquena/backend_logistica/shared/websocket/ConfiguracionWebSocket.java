package com.chaquena.backend_logistica.shared.websocket;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * STOMP con broker simple en memoria: /topic difunde, /user/queue llega a una sesion y /app lo atienden los
 * @MessageMapping. Sin SockJS: el respaldo por sondeo solo anadiria otra forma de fallar.
 */
@Configuration
@EnableWebSocketMessageBroker
public class ConfiguracionWebSocket implements WebSocketMessageBrokerConfigurer {

    static final String PUNTO_DE_CONEXION = "/api/v1/ws";

    private final AutenticacionStomp autenticacion;
    private final TaskScheduler latidos;
    private final String[] origenes;

    /** El planificador va perezoso: la configuracion de Spring que lo define depende de esta clase. */
    public ConfiguracionWebSocket(
            AutenticacionStomp autenticacion,
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler latidos,
            @Value("${app.cors.allowed-origins:http://localhost:81,http://localhost:80,http://localhost:4200}")
            String origenes) {
        this.autenticacion = autenticacion;
        this.latidos = latidos;
        this.origenes = Arrays.stream(origenes.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        // Sin setPreserveReceiveOrder a proposito: procesa las tramas en otro hilo y el rechazo de
        // AutenticacionStomp ya no llega como ERROR; el cliente se quedaba colgado sin saber por que.
        registro.addEndpoint(PUNTO_DE_CONEXION).setAllowedOriginPatterns(origenes);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        registro.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(latidos);
        registro.setApplicationDestinationPrefixes("/app");
        registro.setUserDestinationPrefix("/user");
        registro.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registro) {
        registro.interceptors(autenticacion);
    }
}
