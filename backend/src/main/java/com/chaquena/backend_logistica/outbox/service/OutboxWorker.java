package com.chaquena.backend_logistica.outbox.service;

import com.chaquena.backend_logistica.outbox.domain.EstadoOutboxEnum;
import com.chaquena.backend_logistica.outbox.domain.OutboxEvent;
import com.chaquena.backend_logistica.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Consumidor del patron Transactional Outbox. Toma los eventos pendientes con
 * bloqueo y SKIP LOCKED, de modo que se puedan correr varias instancias sin
 * que se pisen, y aplica reintento con espera creciente hasta mandar el evento
 * a la cola muerta.
 *
 * Mientras backend-facturacion no exista, app.outbox.enabled queda en false:
 * el worker no dispara nada pero el mecanismo esta escrito y probado, y se
 * enciende cambiando una propiedad.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxWorker {

    private final OutboxEventRepository outboxEventRepository;

    @Value("${app.outbox.enabled:false}")
    private boolean habilitado;

    @Value("${app.outbox.destino-url:}")
    private String destinoUrl;

    @Value("${app.outbox.tamano-lote:20}")
    private int tamanoLote;

    @Value("${app.outbox.max-reintentos:5}")
    private int maxReintentos;

    @Scheduled(fixedDelayString = "${app.outbox.intervalo-ms:15000}")
    @Transactional
    public void procesarPendientes() {
        if (!habilitado) {
            return;
        }

        List<OutboxEvent> lote = outboxEventRepository.tomarLote(
                EstadoOutboxEnum.PENDIENTE, ZonedDateTime.now(), PageRequest.of(0, tamanoLote));

        if (lote.isEmpty()) {
            return;
        }

        log.debug("Outbox: procesando {} eventos pendientes", lote.size());
        for (OutboxEvent evento : lote) {
            procesar(evento);
        }
    }

    private void procesar(OutboxEvent evento) {
        try {
            if (destinoUrl == null || destinoUrl.isBlank()) {
                throw new IllegalStateException(
                        "app.outbox.destino-url no esta configurada: no hay a donde enviar el evento.");
            }

            RestClient.create()
                    .post()
                    .uri(destinoUrl)
                    // La clave de idempotencia es el id del evento: si el envio se
                    // repite, facturacion reconoce que ya lo proceso.
                    .header("X-Idempotence-Key", evento.getId().toString())
                    .header("Content-Type", "application/json")
                    .body(evento.getPayload())
                    .retrieve()
                    .toBodilessEntity();

            evento.setStatus(EstadoOutboxEnum.PROCESADO);
            evento.setErrorMessage(null);
            evento.setModifiedBy("OUTBOX_WORKER");
            outboxEventRepository.save(evento);

        } catch (Exception e) {
            registrarFallo(evento, e);
        }
    }

    private void registrarFallo(OutboxEvent evento, Exception e) {
        int intentos = (evento.getRetryCount() != null ? evento.getRetryCount() : 0) + 1;
        evento.setRetryCount(intentos);
        evento.setErrorMessage(e.getMessage());
        evento.setModifiedBy("OUTBOX_WORKER");

        if (intentos >= maxReintentos) {
            evento.setStatus(EstadoOutboxEnum.DEAD_LETTER);
            log.error("Outbox: evento {} a cola muerta tras {} intentos: {}",
                    evento.getId(), intentos, e.getMessage());
        } else {
            // Espera creciente: 30s, 60s, 120s, 240s...
            long segundos = (long) (30 * Math.pow(2, intentos - 1));
            evento.setStatus(EstadoOutboxEnum.PENDIENTE);
            evento.setNextRetryAt(ZonedDateTime.now().plusSeconds(segundos));
            log.warn("Outbox: evento {} fallo (intento {}), reintenta en {}s: {}",
                    evento.getId(), intentos, segundos, e.getMessage());
        }

        outboxEventRepository.save(evento);
    }
}
