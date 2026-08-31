package com.chaquena.backend_logistica.outbox.service.impl;

import com.chaquena.backend_logistica.outbox.domain.EstadoOutboxEnum;
import com.chaquena.backend_logistica.outbox.domain.OutboxEvent;
import com.chaquena.backend_logistica.outbox.repository.OutboxEventRepository;
import com.chaquena.backend_logistica.outbox.service.OutboxService;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent registrar(String eventType, String aggregateType, String aggregateId,
            Map<String, Object> payload) {
        OutboxEvent evento = OutboxEvent.builder()
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .payload(payload)
                .status(EstadoOutboxEnum.PENDIENTE)
                .retryCount(0)
                .nextRetryAt(ZonedDateTime.now())
                .createdBy(UsuarioActual.username())
                .build();
        return outboxEventRepository.save(evento);
    }
}
