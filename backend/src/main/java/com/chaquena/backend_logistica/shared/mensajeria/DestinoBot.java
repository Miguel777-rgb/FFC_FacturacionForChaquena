package com.chaquena.backend_logistica.shared.mensajeria;

/**
 * A quien va dirigido un mensaje saliente.
 *
 * <p>El identificador cambia por completo segun el proveedor: en WhatsApp es un
 * numero de telefono en formato internacional, en Discord es el snowflake de un
 * usuario o de un canal. Lo que no cambia es la distincion entre hablarle a una
 * persona en privado y publicar donde varios lo leen, y esa distincion si tiene
 * consecuencias de negocio: el codigo OTP de una entrega va a una persona, la
 * comanda que espera la cocina va a un canal donde la vea quien este de turno.
 *
 * <p>Se modela como un tipo y no como una cadena con prefijo para que un
 * proveedor sin canales (WhatsApp) pueda rechazar el envio en vez de mandarle
 * la comanda entera a un telefono cualquiera.
 */
public record DestinoBot(Tipo tipo, String id) {

    public enum Tipo {
        /** Conversacion privada con una persona. */
        USUARIO,
        /** Sala compartida. Solo la soportan los proveedores que tienen canales. */
        CANAL
    }

    public static DestinoBot usuario(String id) {
        return new DestinoBot(Tipo.USUARIO, id);
    }

    public static DestinoBot canal(String id) {
        return new DestinoBot(Tipo.CANAL, id);
    }

    public boolean esCanal() {
        return tipo == Tipo.CANAL;
    }
}
