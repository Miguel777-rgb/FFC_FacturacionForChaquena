package com.chaquena.backend_logistica.inventario.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Una partida de un insumo: lo que entro junto, con su costo y su vencimiento.
 *
 * <p>Cada movimiento que suma stock crea un lote, y cada uno que resta consume
 * primero lo que vence antes (FEFO). {@code insumos.stock_actual} sigue siendo
 * el total y la unica cifra que se bloquea; la suma de lo que queda en los
 * lotes de un insumo es igual a ese total.
 *
 * <p>Los nulos dicen algo. Un costo nulo es una compra de la que no se anoto el
 * precio: contarla como cero abarataria el inventario. Un vencimiento nulo es
 * un producto sin fecha, o una fecha que nadie escribio: se consume al final.
 */
@Entity
@Table(name = "lotes_insumo", indexes = @Index(name = "ix_lotes_insumo_insumo", columnList = "insumo_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoteInsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    /**
     * El movimiento del kardex que lo creo. Nulo solo en los lotes iniciales de
     * bd/migracion_06_lotes_iniciales.sql, que agruparon el stock que ya habia.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "control_origen_id")
    private ControlInsumo controlOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @Column(name = "cantidad_inicial", nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidadInicial;

    @Column(name = "cantidad_restante", nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidadRestante;

    /** Soles por unidad de medida del insumo. */
    @Column(name = "costo_unitario", precision = 12, scale = 4)
    private BigDecimal costoUnitario;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

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
        if (this.cantidadInicial == null) this.cantidadInicial = BigDecimal.ZERO;
        if (this.cantidadRestante == null) this.cantidadRestante = this.cantidadInicial;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}
