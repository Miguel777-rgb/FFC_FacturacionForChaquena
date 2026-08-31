package com.chaquena.backend_logistica.outbox.service;

import com.chaquena.backend_logistica.outbox.domain.OutboxEvent;

import java.util.Map;

public interface OutboxService {

    /**
     * Escribe el evento dentro de la misma transaccion que lo origina. Es lo
     * que hace que la comanda y su intencion de facturar se guarden o se
     * pierdan juntas, sin transacciones distribuidas.
     */
    OutboxEvent registrar(String eventType, String aggregateType, String aggregateId,
            Map<String, Object> payload);
}
