package com.chaquena.backend_logistica.cocina.service;

import com.chaquena.backend_logistica.cocina.dto.LlamadoCocinaDto;
import com.chaquena.backend_logistica.shared.websocket.UsuarioStomp;

import java.util.List;
import java.util.UUID;

/**
 * El llamado de cocina al mozo, sin saber como viaja: el WebSocket lo pone el
 * controlador STOMP y el difusor.
 */
public interface LlamadoCocinaService {

    /**
     * Cocina llama para una comanda lista. Si ya hay un llamado pendiente para
     * ella, devuelve ese mismo: volver a llamar repite el aviso, no lo duplica.
     */
    LlamadoCocinaDto llamar(UUID ordenId, UsuarioStomp quien);

    /** Un mozo responde «Voy». Solo el primero lo consigue. */
    LlamadoCocinaDto atender(UUID llamadoId, UsuarioStomp mozo);

    /** Cierra los llamados pendientes de una comanda que salio del pase. */
    List<LlamadoCocinaDto> cerrarPendientesDe(UUID ordenId);

    /**
     * La foto inicial al suscribirse: al mozo, los llamados que esperan; a
     * cocina, los de todas las comandas que siguen en el pase.
     */
    List<LlamadoCocinaDto> estadoPara(UsuarioStomp usuario);
}
