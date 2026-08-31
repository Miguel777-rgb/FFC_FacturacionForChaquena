package com.chaquena.backend_logistica.pedidos.domain;

/**
 * Ciclo de vida de la comanda. PAGADO y CONCLUIDO se agregaron para cubrir el
 * diagrama de contexto.md, donde la caja y el feedback cierran el ciclo
 * despues de la entrega. El estado COMPLETADO del diagrama no es un estado
 * propio: se representa con flag_cierre_platillo mientras la orden sigue en
 * EN_PREPARACION.
 */
public enum EstadoOrdenEnum {
    ENCOLADO,
    EN_PREPARACION,
    EN_DESPACHO,
    ENTREGADO,
    PAGADO,
    CONCLUIDO,
    CANCELADO,
    FRAUDULENTO
}
