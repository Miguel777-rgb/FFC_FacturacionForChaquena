package com.chaquena.backend_logistica.pagos.domain;

public enum EstadoPagoEnum {
    PENDIENTE,   // Registrado, esperando acreditacion (e-wallet o tarjeta)
    CONFIRMADO,  // Dinero acreditado o efectivo recibido
    RECHAZADO,   // La pasarela o el banco lo rechazo
    FRAUDULENTO  // Billete falso o comprobante adulterado
}
