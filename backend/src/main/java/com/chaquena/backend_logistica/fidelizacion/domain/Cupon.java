package com.chaquena.backend_logistica.fidelizacion.domain;

import com.chaquena.backend_logistica.clientes.domain.Cliente;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Cupon de incentivo emitido automaticamente cuando un cliente alcanza la
 * calificacion numero N que define administracion. Sin esta entidad la regla
 * de fidelizacion no tenia donde guardar el codigo, el vencimiento ni si ya
 * se uso.
 */
@Entity
@Table(name = "cupones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cupon {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    @Column(name = "porcentaje_descuento", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeDescuento;

    @Column(name = "monto_descuento", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoDescuento;

    @Column(name = "fecha_emision", nullable = false)
    private ZonedDateTime fechaEmision;

    @Column(name = "fecha_vencimiento", nullable = false)
    private ZonedDateTime fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoCuponEnum estado;

    /** Comanda donde se canjeo, si ya se uso. */
    @Column(name = "orden_canje_id")
    private UUID ordenCanjeId;

    @Column(name = "fecha_canje")
    private ZonedDateTime fechaCanje;

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
        if (this.fechaEmision == null) this.fechaEmision = ZonedDateTime.now();
        if (this.estado == null) this.estado = EstadoCuponEnum.VIGENTE;
        if (this.porcentajeDescuento == null) this.porcentajeDescuento = BigDecimal.ZERO;
        if (this.montoDescuento == null) this.montoDescuento = BigDecimal.ZERO;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }

    public boolean estaVigente() {
        return this.estado == EstadoCuponEnum.VIGENTE
                && this.fechaVencimiento != null
                && this.fechaVencimiento.isAfter(ZonedDateTime.now());
    }
}
