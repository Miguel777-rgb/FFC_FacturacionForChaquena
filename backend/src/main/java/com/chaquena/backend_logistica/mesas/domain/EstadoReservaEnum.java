package com.chaquena.backend_logistica.mesas.domain;

/**
 * Pendiente y confirmada apartan la mesa; las otras tres cierran la reserva y
 * la dejan solo como historia.
 */
public enum EstadoReservaEnum {
    PENDIENTE,
    CONFIRMADA,
    CUMPLIDA,
    CANCELADA,
    NO_ASISTIO
}
