package com.chaquena.backend_logistica.inventario.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "complementos_platillo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplementoPlatillo {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_complemento", nullable = false, length = 20)
    private TipoComplementoEnum tipoComplemento;

    @Column(name = "precio_adicional", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioAdicional;

    // Opcional: Insumo asociado si este complemento requiere descontar stock (ej: lata de gaseosa)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_asociado_id")
    private Insumo insumoAsociado;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

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
        if (this.precioAdicional == null) this.precioAdicional = BigDecimal.ZERO;
        if (this.activo == null) this.activo = true;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}