package com.chaquena.backend_logistica.shared.mensajeria;

/**
 * Lo unico que una maquina de estados necesita saber de un mensaje entrante:
 * por que bot llego, quien escribe y que dijo.
 *
 * <p>Es la frontera entre el proveedor y el negocio. El webhook de Meta entrega
 * un arbol JSON profundo y opcional en casi todos sus niveles; Discord entrega
 * objetos de JDA con su propia jerarquia de eventos. Ninguna de las dos formas
 * llega hasta los servicios de bot: ambos adaptadores construyen este registro
 * y lo que hay aguas arriba no sabe de cual vino.
 *
 * <p>El {@code contenido} unifica los tres tipos de respuesta posible: el texto
 * libre tal cual, y el identificador de la fila o del boton elegido cuando se
 * responde a un mensaje interactivo. Aguas arriba se distinguen por el prefijo
 * del identificador.
 *
 * @param canal           bot por el que entro el mensaje
 * @param remitenteId     telefono en WhatsApp, snowflake de usuario en Discord
 * @param remitenteNombre etiqueta legible para logs y saludos; puede ser nula
 * @param messageId       identificador del mensaje, para el acuse de recibo
 * @param contenido       texto libre o identificador de la opcion elegida
 */
public record MensajeBot(
        CanalBot canal,
        String remitenteId,
        String remitenteNombre,
        String messageId,
        String contenido) {

    /** El remitente, listo para responderle por donde escribio. */
    public DestinoBot origen() {
        return DestinoBot.usuario(remitenteId);
    }

    public boolean vacio() {
        return contenido == null || contenido.isBlank();
    }
}
