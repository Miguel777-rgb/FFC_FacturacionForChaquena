package com.chaquena.backend_logistica.delivery.bot.domain;

/**
 * Pasos de la conversacion del bot de clientes. El paso vive en
 * {@code sesiones_bot.paso_actual} y determina como se interpreta el siguiente
 * mensaje que llegue de esa cuenta: la misma cadena de texto significa una nota
 * del platillo en un paso y una direccion de entrega en otro.
 */
public enum PasoConversacionEnum {

    /** Sesion recien abierta: se saluda y se ofrece la carta. */
    INICIO,

    /** Esperando que elija una categoria, cuando la carta no cabe en una sola lista. */
    ELIGIENDO_CATEGORIA,

    /** Esperando que elija un platillo de la lista. */
    ELIGIENDO_PLATO,

    /** Esperando bebida, postre u otro complemento para el platillo en curso. */
    ELIGIENDO_COMPLEMENTO,

    /** Unico paso de texto libre del platillo: "sin cebolla", "bien cocido". */
    ESCRIBIENDO_NOTA,

    /** Esperando que decida si agrega otro platillo o pasa a cerrar el pedido. */
    DECIDIENDO_MAS_PLATOS,

    /** Esperando que elija delivery o recojo en local. */
    ELIGIENDO_ENTREGA,

    /** Esperando la direccion de entrega, en texto libre. */
    ESCRIBIENDO_DIRECCION,

    /** Esperando el metodo de pago. */
    ELIGIENDO_PAGO,

    /** Resumen enviado, esperando que confirme o cancele. */
    CONFIRMANDO
}
