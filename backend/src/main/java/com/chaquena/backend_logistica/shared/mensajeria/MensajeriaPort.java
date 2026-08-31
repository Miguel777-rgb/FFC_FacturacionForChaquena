package com.chaquena.backend_logistica.shared.mensajeria;

import java.util.List;

/**
 * Puerto de salida hacia el servicio de mensajeria. Todo lo que los bots dicen
 * hacia afuera pasa por aqui.
 *
 * <p>La razon de que exista es economica antes que estetica: WhatsApp Cloud API
 * cobra por conversacion y Discord no, asi que el proveedor tenia que poder
 * cambiarse sin reescribir las maquinas de estado. Lo que se conserva de un
 * proveedor a otro es el vocabulario de la conversacion —decir algo, ofrecer
 * una lista cerrada, ofrecer dos o tres botones— y no la forma del JSON que
 * cada uno espera.
 *
 * <p>Las implementaciones no lanzan excepciones por fallos de red: un mensaje
 * que no sale no puede tumbar la transaccion de negocio que lo disparo. Se
 * registra en el log y la conversacion sigue.
 */
public interface MensajeriaPort {

    /** Nombre con el que se selecciona este adaptador en {@code app.mensajeria.proveedor}. */
    String nombre();

    /**
     * Cuantas filas admite una lista desplegable en este proveedor. WhatsApp
     * corta en 10 y Discord en 25; las maquinas de estado agrupan por categoria
     * cuando la carta no cabe, y necesitan saber el limite real para no dejar
     * platillos fuera de pantalla sin avisar.
     */
    int maxOpciones();

    /** Si el adaptador tiene credenciales y esta listo para enviar. */
    boolean disponible();

    void enviarTexto(CanalBot bot, DestinoBot destino, String texto);

    /**
     * Lista cerrada de opciones. Es el mensaje que sostiene la carta y los
     * complementos: se elige de una lista en lugar de escribir libremente, asi
     * que no hace falta interpretar "kiero 1 ceviche p porfa".
     */
    void enviarOpciones(CanalBot bot, DestinoBot destino, String titulo, String cuerpo,
            String textoBoton, List<OpcionBot> opciones);

    /** Botones de respuesta rapida, para las preguntas de si/no y de dos o tres caminos. */
    void enviarBotones(CanalBot bot, DestinoBot destino, String cuerpo, List<BotonBot> botones);

    /**
     * Acuse de lectura sobre el mensaje entrante. En WhatsApp es el doble check
     * azul; en Discord las interacciones ya se acusan solas al responderlas, de
     * modo que el adaptador no hace nada.
     */
    default void acusarRecibo(CanalBot bot, String messageId) {
        // Sin efecto salvo que el proveedor tenga acuses explicitos.
    }
}
