package com.chaquena.backend_logistica.pedidos.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

import com.chaquena.backend_logistica.clientes.domain.Cliente;

@Entity
@Table(name = "calificaciones_feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalificacionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ CORREGIDO: Relación OneToOne hacia Orden
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id", nullable = false, unique = true)
    private Orden orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "puntaje_atencion", nullable = false)
    private Integer puntajeAtencion;

    @Column(name = "puntaje_comida", nullable = false)
    private Integer puntajeComida;

    @Column(name = "puntaje_lugar", nullable = false)
    private Integer puntajeLugar;

    @Column(name = "comentario", columnDefinition = "TEXT")
    private String comentario;

    // Auditoría
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
        if (this.createdBy == null)
            this.createdBy = "SYSTEM";
        this.dateCreated = ZonedDateTime.now();
        this.lastDateModified = ZonedDateTime.now();
        if (this.modifiedBy == null)
            this.modifiedBy = this.createdBy;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}