package com.chaquena.backend_logistica.cocina.service;

import com.chaquena.backend_logistica.cocina.dto.AvisoLlamadoDto;
import com.chaquena.backend_logistica.cocina.dto.LlamadoCocinaDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publica en los dos temas del llamado. Se llama despues de que el servicio
 * confirma su transaccion: un aviso nunca anuncia algo que no quedo escrito.
 */
@Component
@RequiredArgsConstructor
public class DifusorLlamados {

    public static final String MOZOS = "/topic/llamados/mozos";
    public static final String COCINA = "/topic/llamados/cocina";

    private final SimpMessagingTemplate mensajeria;

    public void llamado(LlamadoCocinaDto llamado, int mozosConectados) {
        aLosDos(AvisoLlamadoDto.de("llamado", llamado, mozosConectados));
    }

    public void atendido(LlamadoCocinaDto llamado) {
        aLosDos(AvisoLlamadoDto.de("atendido", llamado, null));
    }

    public void cerrados(List<LlamadoCocinaDto> llamados) {
        aLosDos(AvisoLlamadoDto.cerrados(llamados));
    }

    /** Solo a cocina: es la que necesita saber si alguien la va a oir. */
    public void presencia(int mozosConectados) {
        mensajeria.convertAndSend(COCINA, AvisoLlamadoDto.presencia(mozosConectados));
    }

    private void aLosDos(AvisoLlamadoDto aviso) {
        mensajeria.convertAndSend(MOZOS, aviso);
        mensajeria.convertAndSend(COCINA, aviso);
    }
}
