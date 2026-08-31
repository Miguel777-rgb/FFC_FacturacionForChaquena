package com.chaquena.backend_logistica.outbox.domain;

public enum EstadoOutboxEnum {
    PENDIENTE,
    PROCESADO,
    ERROR,
    DEAD_LETTER
}