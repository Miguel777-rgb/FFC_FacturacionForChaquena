package com.chaquena.backend_logistica.inventario.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "promociones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promocion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "porcentaje_descuento", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeDescuento;

    @Column(name = "monto_descuento", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoDescuento;

    @Column(name = "requiere_insumo_extra", nullable = false)
    private Boolean requiereInsumoExtra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_extra_id")
    private Insumo insumoExtra;

    @Column(name = "fecha_inicio", nullable = false)
    private ZonedDateTime fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private ZonedDateTime fechaFin;

    @Column(name = "activa", nullable = false)
    private Boolean activa;

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
        if (this.porcentajeDescuento == null) this.porcentajeDescuento = BigDecimal.ZERO;
        if (this.montoDescuento == null) this.montoDescuento = BigDecimal.ZERO;
        if (this.requiereInsumoExtra == null) this.requiereInsumoExtra = false;
        if (this.activa == null) this.activa = true;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}