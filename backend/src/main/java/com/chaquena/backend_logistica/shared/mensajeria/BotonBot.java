package com.chaquena.backend_logistica.shared.mensajeria;

/**
 * Boton de respuesta rapida. Igual que {@link OpcionBot}, el {@code id} es lo
 * que llega de vuelta al pulsarlo.
 *
 * <p>El estilo es una pista visual, no una regla: un proveedor que no distinga
 * colores puede ignorarlo sin que la conversacion cambie de significado.
 */
public record BotonBot(String id, String etiqueta, Estilo estilo) {

    public enum Estilo {
        /** Accion principal del mensaje. */
        PRIMARIO,
        /** Alternativa neutra. */
        SECUNDARIO,
        /** Confirmacion de algo que avanza el flujo. */
        EXITO,
        /** Cancelar, rechazar o cualquier cosa que se deshaga mal. */
        PELIGRO
    }

    public static BotonBot de(String id, String etiqueta) {
        return new BotonBot(id, etiqueta, Estilo.SECUNDARIO);
    }

    public static BotonBot primario(String id, String etiqueta) {
        return new BotonBot(id, etiqueta, Estilo.PRIMARIO);
    }

    public static BotonBot exito(String id, String etiqueta) {
        return new BotonBot(id, etiqueta, Estilo.EXITO);
    }

    public static BotonBot peligro(String id, String etiqueta) {
        return new BotonBot(id, etiqueta, Estilo.PELIGRO);
    }
}
