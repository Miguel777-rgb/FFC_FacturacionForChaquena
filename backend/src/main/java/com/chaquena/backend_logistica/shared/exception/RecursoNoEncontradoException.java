package com.chaquena.backend_logistica.shared.exception;

/**
 * El recurso solicitado no existe. Se traduce a HTTP 404.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public static RecursoNoEncontradoException de(String entidad, Object id) {
        return new RecursoNoEncontradoException("No se encontro " + entidad + " con identificador " + id + ".");
    }
}
