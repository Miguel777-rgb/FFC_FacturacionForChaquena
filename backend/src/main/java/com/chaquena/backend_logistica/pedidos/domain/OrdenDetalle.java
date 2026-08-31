package com.chaquena.backend_logistica.pedidos.domain;

import com.chaquena.backend_logistica.inventario.domain.Platillo;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orden_detalles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id", nullable = false)
    private Orden orden;

    // ✅ CORREGIDO: Relación ManyToOne hacia Platillo (Crea la FK física en
    // PostgreSQL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platillo_id", nullable = false)
    private Platillo platillo;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "precio_venta_producto", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioVentaProducto;

    @Column(name = "monto_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoSubtotal;

    @Column(name = "excepciones_nota", columnDefinition = "TEXT")
    private String excepcionesNota;

    @Builder.Default
    @OneToMany(mappedBy = "ordenDetalle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrdenDetalleComplemento> complementos = new ArrayList<>();

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

    public void addComplemento(OrdenDetalleComplemento complemento) {
        complementos.add(complemento);
        complemento.setOrdenDetalle(this);
    }
}