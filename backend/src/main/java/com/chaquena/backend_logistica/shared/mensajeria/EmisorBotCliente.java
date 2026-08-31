package com.chaquena.backend_logistica.shared.mensajeria;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Emisor del Bot OUT: todo lo que sale por aqui va por la cuenta de cara al
 * cliente. Lo usan el bot de pedidos, el aviso de tiempo estimado que fija
 * cocina y las notificaciones de delivery (estado del despacho y codigo OTP de
 * entrega), que nunca deben salir por la cuenta interna del personal.
 */
@Service
@RequiredArgsConstructor
public class EmisorBotCliente {

    private final MensajeriaService mensajeria;

    public int maxOpciones() {
        return mensajeria.maxOpciones();
    }

    public void enviarTexto(String usuarioId, String mensaje) {
        mensajeria.enviarTexto(CanalBot.OUT, DestinoBot.usuario(usuarioId), mensaje);
    }

    public void enviarOpciones(String usuarioId, String titulo, String cuerpo,
            String textoBoton, List<OpcionBot> opciones) {
        mensajeria.enviarOpciones(CanalBot.OUT, DestinoBot.usuario(usuarioId), titulo, cuerpo,
                textoBoton, opciones);
    }

    public void enviarBotones(String usuarioId, String cuerpo, List<BotonBot> botones) {
        mensajeria.enviarBotones(CanalBot.OUT, DestinoBot.usuario(usuarioId), cuerpo, botones);
    }

    public void acusarRecibo(String messageId) {
        mensajeria.acusarRecibo(CanalBot.OUT, messageId);
    }
}
