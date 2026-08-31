package com.chaquena.backend_logistica.outbox.controller;

import com.chaquena.backend_logistica.outbox.domain.EstadoOutboxEnum;
import com.chaquena.backend_logistica.outbox.domain.OutboxEvent;
import com.chaquena.backend_logistica.outbox.dto.OutboxEventDto;
import com.chaquena.backend_logistica.outbox.repository.OutboxEventRepository;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outbox")
@RequiredArgsConstructor
@Tag(name = "Outbox", description = "Eventos hacia facturacion: monitoreo y reintento manual")
public class OutboxController {

    private final OutboxEventRepository outboxEventRepository;

    @GetMapping("/eventos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eventos por estado, para ver que no llego a facturacion")
    public ResponseEntity<PageResponseDto<OutboxEventDto>> listar(
            @RequestParam(required = false) EstadoOutboxEnum status,
            @PageableDefault(size = 30) Pageable pageable) {
        if (status != null) {
            return ResponseEntity.ok(PageResponseDto.de(
                    outboxEventRepository.findByStatusOrderByDateCreatedDesc(status, pageable),
                    e -> OutboxEventDto.fromEntity(e, false)));
        }
        return ResponseEntity.ok(PageResponseDto.de(outboxEventRepository.findAll(pageable),
                e -> OutboxEventDto.fromEntity(e, false)));
    }

    @GetMapping("/eventos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Detalle del evento con su payload completo")
    public ResponseEntity<OutboxEventDto> obtener(@PathVariable UUID id) {
        OutboxEvent evento = outboxEventRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el evento de outbox", id));
        return ResponseEntity.ok(OutboxEventDto.fromEntity(evento, true));
    }

    @PostMapping("/eventos/{id}/reintentar")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Devolver a la cola un evento en error o en cola muerta")
    public ResponseEntity<OutboxEventDto> reintentar(@PathVariable UUID id) {
        OutboxEvent evento = outboxEventRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el evento de outbox", id));

        if (evento.getStatus() == EstadoOutboxEnum.PROCESADO) {
            throw new ConflictoException("El evento ya fue procesado con exito.");
        }

        evento.setStatus(EstadoOutboxEnum.PENDIENTE);
        evento.setRetryCount(0);
        evento.setNextRetryAt(ZonedDateTime.now());
        evento.setErrorMessage(null);
        evento.setModifiedBy(UsuarioActual.username());

        return ResponseEntity.ok(OutboxEventDto.fromEntity(outboxEventRepository.save(evento), false));
    }

    @GetMapping("/salud")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Conteo de eventos por estado")
    public ResponseEntity<Map<String, Long>> salud() {
        return ResponseEntity.ok(Map.of(
                "pendientes", outboxEventRepository.countByStatus(EstadoOutboxEnum.PENDIENTE),
                "procesados", outboxEventRepository.countByStatus(EstadoOutboxEnum.PROCESADO),
                "errores", outboxEventRepository.countByStatus(EstadoOutboxEnum.ERROR),
                "colaMuerta", outboxEventRepository.countByStatus(EstadoOutboxEnum.DEAD_LETTER)));
    }
}
