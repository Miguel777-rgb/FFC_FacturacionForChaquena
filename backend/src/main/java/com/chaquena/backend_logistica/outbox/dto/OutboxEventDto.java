package com.chaquena.backend_logistica.outbox.dto;

import com.chaquena.backend_logistica.outbox.domain.EstadoOutboxEnum;
import com.chaquena.backend_logistica.outbox.domain.OutboxEvent;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEventDto {

    private UUID id;
    private String eventType;
    private String aggregateType;
    private String aggregateId;
    private EstadoOutboxEnum status;
    private Integer retryCount;
    private ZonedDateTime nextRetryAt;
    private String errorMessage;
    private ZonedDateTime fecha;
    private Map<String, Object> payload;

    public static OutboxEventDto fromEntity(OutboxEvent e, boolean incluirPayload) {
        return OutboxEventDto.builder()
                .id(e.getId())
                .eventType(e.getEventType())
                .aggregateType(e.getAggregateType())
                .aggregateId(e.getAggregateId())
                .status(e.getStatus())
                .retryCount(e.getRetryCount())
                .nextRetryAt(e.getNextRetryAt())
                .errorMessage(e.getErrorMessage())
                .fecha(e.getDateCreated())
                .payload(incluirPayload ? e.getPayload() : null)
                .build();
    }
}
