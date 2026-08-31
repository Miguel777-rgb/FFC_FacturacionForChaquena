package com.chaquena.backend_logistica.inventario.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "platillos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Platillo {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaPlatillo categoria;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre; // Ej: "Porción de Papas Fritas", "Porción de Chorizo", "Lomo Saltado"

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    // Renombrado: Precio de Venta Sugerido / Base del Producto o Porción
    @Column(name = "precio_venta_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioVentaBase;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Builder.Default
    @OneToMany(mappedBy = "platillo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InsumoPlatillo> receta = new ArrayList<>();

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
        if (this.createdBy == null) this.createdBy = "SYSTEM";
        this.dateCreated = ZonedDateTime.now();
        this.lastDateModified = ZonedDateTime.now();
        if (this.modifiedBy == null) this.modifiedBy = this.createdBy;
        if (this.activo == null) this.activo = true;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}