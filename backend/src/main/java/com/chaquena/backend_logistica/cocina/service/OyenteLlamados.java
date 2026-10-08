package com.chaquena.backend_logistica.cocina.service;

import com.chaquena.backend_logistica.cocina.dto.LlamadoCocinaDto;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenCambiadoEvent;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.shared.websocket.PresenciaCambiadaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/** Lo que cambia los llamados sin que nadie pulse nada. */
@Component
@RequiredArgsConstructor
public class OyenteLlamados {

    private final LlamadoCocinaService servicio;
    private final DifusorLlamados difusor;

    /**
     * La comanda salio del pase —entregada, a despacho o cancelada— sin que
     * nadie respondiera: sus avisos desaparecen de los celulares.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCambiarEstado(EstadoOrdenCambiadoEvent evento) {
        if (evento.estado() == EstadoOrdenEnum.EN_PREPARACION) {
            return;
        }
        List<LlamadoCocinaDto> cerrados = servicio.cerrarPendientesDe(evento.ordenId());
        if (!cerrados.isEmpty()) {
            difusor.cerrados(cerrados);
        }
    }

    @EventListener
    public void alCambiarPresencia(PresenciaCambiadaEvent evento) {
        difusor.presencia(evento.mozosConectados());
    }
}
