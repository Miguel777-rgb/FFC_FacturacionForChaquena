package com.chaquena.backend_logistica.pedidos.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.chaquena.backend_logistica.clientes.domain.Cliente;
import com.chaquena.backend_logistica.inventario.domain.Promocion;
import com.chaquena.backend_logistica.mesas.domain.Mesa;

@Entity
@Table(name = "ordenes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "mozo_id")
    private UUID mozoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_orden", nullable = false, length = 20)
    private TipoOrdenEnum tipoOrden;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_origen", nullable = false, length = 20)
    private CanalOrigenEnum canalOrigen;

    @Column(name = "mesa_numero", length = 10)
    private String mesaNumero;

    /**
     * Mesa fisica del salon. mesaNumero se conserva como denormalizacion para
     * no romper las ordenes historicas ni los tickets ya impresos.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id")
    private Mesa mesa;

    @Column(name = "direccion_delivery", columnDefinition = "TEXT")
    private String direccionDelivery;

    @Column(name = "codigo_otp_entrega", length = 6)
    private String codigoOtpEntrega;

    @Column(name = "scoring_riesgo_orden", nullable = false)
    private Integer scoringRiesgoOrden;

    @Column(name = "monto_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoSubtotal;

    @Column(name = "monto_descuento", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoDescuento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promocion_id")
    private Promocion promocion;

    @Column(name = "cupon_codigo", length = 20)
    private String cuponCodigo;

    /** Obligatorio al cancelar: alimenta el historico de cancelaciones. */
    @Column(name = "motivo_cancelacion", columnDefinition = "TEXT")
    private String motivoCancelacion;

    @Column(name = "monto_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pago", nullable = false, length = 20)
    private TipoPagoEnum tipoPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoOrdenEnum estado;

    @Column(name = "tiempo_inicio_global")
    private ZonedDateTime tiempoInicioGlobal;

    @Column(name = "tiempo_fin_global")
    private ZonedDateTime tiempoFinGlobal;

    @Column(name = "flag_cierre_recepcion", nullable = false)
    private Boolean flagCierreRecepcion;

    @Column(name = "flag_cierre_platillo", nullable = false)
    private Boolean flagCierrePlatillo;

    @Column(name = "flag_cierre_despacho", nullable = false)
    private Boolean flagCierreDespacho;

    // Subcronometros por etapa (seccion F de contexto.md)
    @Column(name = "tiempo_cierre_recepcion")
    private ZonedDateTime tiempoCierreRecepcion;

    @Column(name = "tiempo_inicio_cocina")
    private ZonedDateTime tiempoInicioCocina;

    /**
     * Minutos que cocina promete al tomar la comanda. No es una medicion sino
     * una promesa: los cronometros de arriba dicen cuanto tardo de verdad y
     * este campo, cuanto se dijo que tardaria. La diferencia entre ambos es lo
     * que hace util el dato, tanto para avisar al cliente como para saber si la
     * cocina se conoce a si misma.
     *
     * <p>Lleva "cocina" en el nombre porque no es el unico tiempo estimado de
     * una comanda: {@code orden_delivery_info.tiempo_estimado_minutos} guarda el
     * del trayecto del reparto, que es otra cosa y de otra etapa.
     */
    @Column(name = "tiempo_estimado_cocina_minutos")
    private Integer tiempoEstimadoCocinaMinutos;

    @Column(name = "tiempo_cierre_platillo")
    private ZonedDateTime tiempoCierrePlatillo;

    @Column(name = "tiempo_cierre_despacho")
    private ZonedDateTime tiempoCierreDespacho;

    @Builder.Default
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrdenDetalle> detalles = new ArrayList<>();

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
        if (this.estado == null)
            this.estado = EstadoOrdenEnum.ENCOLADO;
        if (this.canalOrigen == null)
            this.canalOrigen = CanalOrigenEnum.POS;
        if (this.montoDescuento == null)
            this.montoDescuento = BigDecimal.ZERO;
        if (this.scoringRiesgoOrden == null)
            this.scoringRiesgoOrden = 0;
        if (this.flagCierreRecepcion == null)
            this.flagCierreRecepcion = false;
        if (this.flagCierrePlatillo == null)
            this.flagCierrePlatillo = false;
        if (this.flagCierreDespacho == null)
            this.flagCierreDespacho = false;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }

    public void addDetalle(OrdenDetalle detalle) {
        detalles.add(detalle);
        detalle.setOrden(this);
    }
}