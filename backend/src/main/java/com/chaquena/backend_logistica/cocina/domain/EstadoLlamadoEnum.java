package com.chaquena.backend_logistica.cocina.domain;

/**
 * PENDIENTE espera a un mozo; ATENDIDO ya tiene quien va; CERRADO es el que
 * nadie atendio porque la comanda salio del pase por otro camino.
 */
public enum EstadoLlamadoEnum {
    PENDIENTE,
    ATENDIDO,
    CERRADO
}
