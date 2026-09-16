package com.chaquena.backend_logistica.tiemporeal.controller;

import com.chaquena.backend_logistica.tiemporeal.dto.AvisoTiempoRealDto;
import com.chaquena.backend_logistica.tiemporeal.service.DifusorTiempoReal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * El stream de avisos en tiempo real, por Server-Sent Events.
 *
 * <p>Cualquier sesion puede abrirlo; lo que recibe depende de su cargo. El
 * navegador lo lee con {@code fetch} y no con {@code EventSource}, porque este
 * no deja mandar la cabecera {@code Authorization}.
 */
@RestController
@RequestMapping("/api/v1/eventos")
@RequiredArgsConstructor
@Tag(name = "Tiempo real", description = "Avisos de cambios por Server-Sent Events")
public class EventoController {

    private static final String PREFIJO_DE_CARGO = "ROLE_";

    private final DifusorTiempoReal difusor;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "streamEventos",
            summary = "Avisos de lo que cambia en el local, filtrados por cargo",
            description = "Emite 'listo' al conectar, un 'aviso' con el tema por cada rafaga de cambios "
                    + "y un comentario cada 25 segundos. El servidor cierra la conexion a los diez "
                    + "minutos: el cliente reconecta con el token vigente.")
    @ApiResponse(responseCode = "200", description = "Stream abierto",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(implementation = AvisoTiempoRealDto.class)))
    public SseEmitter streamEventos(Authentication autenticacion, HttpServletResponse respuesta) {
        // Un proxy que guarda la respuesta hasta llenarla retendria los avisos.
        respuesta.setHeader("X-Accel-Buffering", "no");
        respuesta.setHeader("Cache-Control", "no-cache");

        Set<String> cargos = autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(PREFIJO_DE_CARGO))
                .map(a -> a.substring(PREFIJO_DE_CARGO.length()))
                .collect(Collectors.toSet());
        return difusor.suscribir(cargos);
    }
}
