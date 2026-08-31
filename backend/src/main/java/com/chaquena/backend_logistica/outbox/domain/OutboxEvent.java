package com.chaquena.backend_logistica.outbox.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType; // Ej: 'FACTURA_REQUERIDA'

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType; // Ej: 'ORDEN'

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId; // UUID de la orden como String

    // Mapeo nativo de Hibernate 6 para almacenar el payload completo de la orden en JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EstadoOutboxEnum status;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private ZonedDateTime nextRetryAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // -----------------------------------------------------
    // Campos de Auditoría Obligatorios
    // -----------------------------------------------------
    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "date_created", nullable = false)
    private ZonedDateTime dateCreated;

    @Column(name = "modified_by", nullable = false, length = 50)
    private String modifiedBy;

    @Column(name = "last_date_modified", nullable = false)
    private ZonedDateTime lastDateModified;

    @PrePersist
    public void prePersist() {
        if (this.createdBy == null) this.createdBy = "SYSTEM";
        this.dateCreated = ZonedDateTime.now();
        this.lastDateModified = ZonedDateTime.now();
        if (this.modifiedBy == null) this.modifiedBy = this.createdBy;
        if (this.status == null) this.status = EstadoOutboxEnum.PENDIENTE;
        if (this.retryCount == null) this.retryCount = 0;
        if (this.nextRetryAt == null) this.nextRetryAt = ZonedDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}