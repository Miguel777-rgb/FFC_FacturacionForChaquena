package com.chaquena.backend_logistica.pagos.domain;

import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.pedidos.domain.TipoPagoEnum;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Cobro individual de una comanda. Se modela aparte de ordenes.tipo_pago
 * porque una orden puede tener varios intentos y varios metodos: un pago
 * fallido que se reintenta con otra via, o un pago dividido entre comensales.
 * Es tambien el unico lugar donde vive el vuelto, el comprobante de la
 * billetera digital y la marca de fraude.
 */
@Entity
@Table(name = "pagos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id", nullable = false)
    private Orden orden;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pago", nullable = false, length = 20)
    private TipoPagoEnum tipoPago;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    /** Solo para efectivo: lo que el cliente entrego en mano. */
    @Column(name = "monto_entregado", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoEntregado;

    /** Solo para efectivo: montoEntregado - monto. */
    @Column(name = "vuelto", nullable = false, precision = 10, scale = 2)
    private BigDecimal vuelto;

    /** Codigo de operacion de Yape/Plin o numero de voucher del POS fisico. */
    @Column(name = "referencia", length = 120)
    private String referencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPagoEnum estado;

    @Column(name = "es_fraudulento", nullable = false)
    private Boolean esFraudulento;

    @Column(name = "observacion", columnDefinition = "TEXT")
    private String observacion;

    @Column(name = "cajero_id")
    private UUID cajeroId;

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
        if (this.estado == null) this.estado = EstadoPagoEnum.PENDIENTE;
        if (this.esFraudulento == null) this.esFraudulento = false;
        // En tarjeta y billetera no hay efectivo sobre el mostrador: se entrega
        // el importe exacto y el vuelto es cero, no "se desconoce".
        if (this.montoEntregado == null) this.montoEntregado = this.monto;
        if (this.vuelto == null) this.vuelto = BigDecimal.ZERO;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}
