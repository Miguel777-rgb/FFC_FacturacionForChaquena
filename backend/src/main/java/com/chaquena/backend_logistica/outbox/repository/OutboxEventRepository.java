package com.chaquena.backend_logistica.outbox.repository;

import com.chaquena.backend_logistica.outbox.domain.EstadoOutboxEnum;
import com.chaquena.backend_logistica.outbox.domain.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    Page<OutboxEvent> findByStatusOrderByDateCreatedDesc(EstadoOutboxEnum status, Pageable pageable);

    /**
     * Lote de eventos listos para enviar. SKIP LOCKED permite correr varias
     * instancias del worker sin que se pisen el mismo evento.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select e from OutboxEvent e
            where e.status = :status and e.nextRetryAt <= :momento
            order by e.dateCreated asc
            """)
    List<OutboxEvent> tomarLote(@Param("status") EstadoOutboxEnum status,
            @Param("momento") ZonedDateTime momento, Pageable pageable);

    long countByStatus(EstadoOutboxEnum status);
}
