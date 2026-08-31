package com.chaquena.backend_logistica.shared.exception;

/**
 * Las credenciales presentadas no dan acceso: token invalido, expirado, emitido
 * para otra aplicacion, o una identidad valida que no corresponde a ningun
 * trabajador dado de alta. Se traduce a HTTP 401.
 *
 * <p>Es una categoria distinta de {@link org.springframework.security.access.AccessDeniedException},
 * que cubre el caso contrario: el usuario esta autenticado pero su cargo no
 * alcanza para la operacion (403).
 */
public class AutenticacionFallidaException extends RuntimeException {

    public AutenticacionFallidaException(String mensaje) {
        super(mensaje);
    }
}
