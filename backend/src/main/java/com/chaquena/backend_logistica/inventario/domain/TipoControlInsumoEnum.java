package com.chaquena.backend_logistica.inventario.domain;

public enum TipoControlInsumoEnum {
    ENTRADA_COMPRA,           // Ingreso de insumos crudos (NO_COCIDO) por proveedor
    TRANSFORMACION_COCIDO,    // Transformación: Pasa de NO_COCIDO a COCIDO (Ej: hornear pollos)
    SALIDA_VENTA,             // Descuento automático por comanda consumida
    MERMA_DESPERDICIO,        // Insumo vencido, quemado o dañado
    AJUSTE_AUDITORIA          // Corrección manual tras conteo físico de cierre de caja
}