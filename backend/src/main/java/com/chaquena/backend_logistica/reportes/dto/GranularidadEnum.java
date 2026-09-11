package com.chaquena.backend_logistica.reportes.dto;

/**
 * Tamano del bloque en el que se agrupa la serie de ventas. HORA sirve para
 * leer la forma de un dia —donde estan los dos picos de servicio—; DIA, para
 * comparar semanas.
 */
public enum GranularidadEnum {
    HORA,
    DIA
}
