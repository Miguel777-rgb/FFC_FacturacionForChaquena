package com.chaquena.backend_logistica.shared.mensajeria;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Emisor del Bot IN: todo lo que sale por aqui va por la cuenta interna del
 * personal. Es una fachada delgada sobre {@link MensajeriaService} que fija la
 * identidad {@link CanalBot#IN}, de forma que el bot de stock, el del mozo y el
 * de cocina no puedan escribirle a un cliente ni aunque se lo pidan.
 *
 * <p>Para hablarle a un cliente esta el emisor gemelo {@link EmisorBotCliente}.
 */
@Service
@RequiredArgsConstructor
public class EmisorBotInterno {

    private final MensajeriaService mensajeria;

    public int maxOpciones() {
        return mensajeria.maxOpciones();
    }

    public void enviarTexto(String usuarioId, String mensaje) {
        mensajeria.enviarTexto(CanalBot.IN, DestinoBot.usuario(usuarioId), mensaje);
    }

    public void enviarOpciones(String usuarioId, String titulo, String cuerpo,
            String textoBoton, List<OpcionBot> opciones) {
        mensajeria.enviarOpciones(CanalBot.IN, DestinoBot.usuario(usuarioId), titulo, cuerpo,
                textoBoton, opciones);
    }

    public void enviarBotones(String usuarioId, String cuerpo, List<BotonBot> botones) {
        mensajeria.enviarBotones(CanalBot.IN, DestinoBot.usuario(usuarioId), cuerpo, botones);
    }

    /** Publica en una sala compartida: el tablero de cocina y los avisos de turno. */
    public void publicarEnCanal(String canalId, String mensaje) {
        mensajeria.enviarTexto(CanalBot.IN, DestinoBot.canal(canalId), mensaje);
    }

    public void publicarBotonesEnCanal(String canalId, String cuerpo, List<BotonBot> botones) {
        mensajeria.enviarBotones(CanalBot.IN, DestinoBot.canal(canalId), cuerpo, botones);
    }

    public void acusarRecibo(String messageId) {
        mensajeria.acusarRecibo(CanalBot.IN, messageId);
    }
}
