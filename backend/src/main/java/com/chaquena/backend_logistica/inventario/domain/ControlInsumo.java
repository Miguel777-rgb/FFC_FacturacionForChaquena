package com.chaquena.backend_logistica.inventario.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "controles_insumo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ControlInsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    // ID del trabajador (Chef, Almacenero o Admin) que ejecuta el control
    @Column(name = "trabajador_id", nullable = false)
    private UUID trabajadorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_control", nullable = false, length = 30)
    private TipoControlInsumoEnum tipoControl;

    @Column(name = "cantidad", nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad; // Positivo para entradas, negativo o absoluto según tipo

    @Column(name = "stock_anterior", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockAnterior;

    @Column(name = "stock_nuevo", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockNuevo;

    @Column(name = "motivo_observacion", columnDefinition = "TEXT")
    private String motivoObservacion; // Ej: "Preparación de 10kg de arroz cocido", "Carne vencida lote #4"

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
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}