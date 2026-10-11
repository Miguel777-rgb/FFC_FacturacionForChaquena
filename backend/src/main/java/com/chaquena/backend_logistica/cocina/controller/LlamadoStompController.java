package com.chaquena.backend_logistica.cocina.controller;

import com.chaquena.backend_logistica.cocina.dto.AtenderRequestDto;
import com.chaquena.backend_logistica.cocina.dto.AvisoLlamadoDto;
import com.chaquena.backend_logistica.cocina.dto.LlamarRequestDto;
import com.chaquena.backend_logistica.cocina.service.DifusorLlamados;
import com.chaquena.backend_logistica.cocina.service.LlamadoCocinaService;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.websocket.PresenciaWebSocket;
import com.chaquena.backend_logistica.shared.websocket.UsuarioStomp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/** Puerta STOMP del llamado: llamar, atender («Voy») y la foto inicial; los permisos ya los vio el interceptor. */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LlamadoStompController {

    private final LlamadoCocinaService servicio;
    private final DifusorLlamados difusor;
    private final PresenciaWebSocket presencia;

    @MessageMapping("/llamados/llamar")
    public void llamar(@Payload LlamarRequestDto pedido, Principal principal) {
        difusor.llamado(servicio.llamar(pedido.ordenId(), usuario(principal)), presencia.mozosConectados());
    }

    @MessageMapping("/llamados/atender")
    public void atender(@Payload AtenderRequestDto pedido, Principal principal) {
        difusor.atendido(servicio.atender(pedido.llamadoId(), usuario(principal)));
    }

    /** Solo a quien se suscribe, una vez: asi un llamado espera al mozo que entra tarde. */
    @SubscribeMapping("/llamados/estado")
    public AvisoLlamadoDto estado(Principal principal) {
        return AvisoLlamadoDto.estado(servicio.estadoPara(usuario(principal)), presencia.mozosConectados());
    }

    /** El error vuelve solo a la sesion que lo provoco, por su cola personal. */
    @MessageExceptionHandler({ConflictoException.class, RecursoNoEncontradoException.class,
            IllegalArgumentException.class})
    @SendToUser(destinations = "/queue/llamados", broadcast = false)
    public AvisoLlamadoDto error(RuntimeException e) {
        return AvisoLlamadoDto.error(e.getMessage());
    }

    private static UsuarioStomp usuario(Principal principal) {
        if (principal instanceof UsuarioStomp usuario) {
            return usuario;
        }
        throw new IllegalArgumentException("La conexion no esta autenticada.");
    }
}
