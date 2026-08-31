package com.chaquena.backend_logistica.pedidos.domain;

import com.chaquena.backend_logistica.inventario.domain.ComplementoPlatillo;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "orden_detalle_complementos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenDetalleComplemento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_detalle_id", nullable = false)
    private OrdenDetalle ordenDetalle;

    // ✅ CORREGIDO: Relación ManyToOne hacia ComplementoPlatillo (Crea la FK física)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complemento_id", nullable = false)
    private ComplementoPlatillo complemento;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "precio_venta_complemento", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioVentaComplemento;

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
        if (this.cantidad == null)
            this.cantidad = 1;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}