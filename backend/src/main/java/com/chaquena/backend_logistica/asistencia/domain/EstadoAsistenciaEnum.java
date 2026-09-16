package com.chaquena.backend_logistica.asistencia.domain;

/**
 * Como va alguien hoy respecto de su turno. No se guarda: se calcula con el
 * turno, la marcacion y la hora.
 */
public enum EstadoAsistenciaEnum {
    /** Su turno todavia no empieza, o empezo hace menos que la tolerancia. */
    POR_LLEGAR,
    /** Marco entrada y no ha marcado salida. */
    DENTRO,
    /** Marco entrada y salida. */
    SALIO,
    /** Su turno ya empezo, paso la tolerancia y no ha marcado. Todavia puede llegar. */
    NO_LLEGA,
    /** Su turno termino sin ninguna entrada. */
    FALTO
}
