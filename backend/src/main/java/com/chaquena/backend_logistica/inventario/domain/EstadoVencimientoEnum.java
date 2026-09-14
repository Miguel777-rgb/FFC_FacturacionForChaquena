package com.chaquena.backend_logistica.inventario.domain;

/** Como esta un lote respecto de su fecha, contado en dias del local. */
public enum EstadoVencimientoEnum {
    /** La fecha ya paso. */
    VENCIDO,
    /** Vence hoy o dentro de los dias de aviso: hay que usarlo primero. */
    POR_VENCER,
    VIGENTE,
    /** Sin fecha: no vence o nadie la anoto. */
    SIN_VENCIMIENTO
}
