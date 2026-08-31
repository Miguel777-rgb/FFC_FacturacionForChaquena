package com.chaquena.backend_logistica.shared.exception;

/**
 * La operacion es valida en si misma pero choca con el estado actual del
 * sistema: una transicion de estado ilegal, un duplicado o una baja
 * bloqueada por dependencias. Se traduce a HTTP 409.
 */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
