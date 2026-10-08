package com.chaquena.backend_logistica.cocina.domain;

import com.chaquena.backend_logistica.pedidos.domain.Orden;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Cocina llamo al mozo para que recoja una comanda lista.
 *
 * <p>El aviso viaja por WebSocket; la fila es lo que queda: quien llamo, quien
 * respondio y cuando. De ahi sale el tiempo de respuesta que ve cocina.
 *
 * <p>Los nombres se guardan junto al usuario porque son los de ese momento: el
 * registro dice quien respondio aquella noche aunque despues cambie su ficha.
 */
@Entity
@Table(name = "llamados_cocina", indexes = {
        @Index(name = "ix_llamados_cocina_estado", columnList = "estado"),
        @Index(name = "ix_llamados_cocina_orden", columnList = "orden_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlamadoCocina {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id", nullable = false)
    private Orden orden;

    @Column(name = "llamado_por", nullable = false, length = 100)
    private String llamadoPor;

    @Column(name = "llamado_por_nombre", nullable = false, length = 120)
    private String llamadoPorNombre;

    @Column(name = "llamado_en", nullable = false)
    private ZonedDateTime llamadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoLlamadoEnum estado;

    /** Nulo con sentido: todavia no respondio nadie. */
    @Column(name = "atendido_por", length = 100)
    private String atendidoPor;

    @Column(name = "atendido_por_nombre", length = 120)
    private String atendidoPorNombre;

    @Column(name = "atendido_en")
    private ZonedDateTime atendidoEn;

    /** Cuando se cerro sin respuesta, porque la comanda salio del pase. */
    @Column(name = "cerrado_en")
    private ZonedDateTime cerradoEn;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "date_created", nullable = false)
    private ZonedDateTime dateCreated;

    @Column(name = "modified_by", nullable = false, length = 100)
    private String modifiedBy;

    @Column(name = "last_date_modified", nullable = false)
    private ZonedDateTime lastDateModified;

    @PrePersist
    void alCrear() {
        ZonedDateTime ahora = ZonedDateTime.now();
        this.dateCreated = ahora;
        this.lastDateModified = ahora;
        if (this.llamadoEn == null) this.llamadoEn = ahora;
        if (this.estado == null) this.estado = EstadoLlamadoEnum.PENDIENTE;
        if (this.createdBy == null) this.createdBy = this.llamadoPor != null ? this.llamadoPor : "SYSTEM";
        if (this.modifiedBy == null) this.modifiedBy = this.createdBy;
    }

    @PreUpdate
    void alModificar() {
        this.lastDateModified = ZonedDateTime.now();
        if (this.modifiedBy == null) this.modifiedBy = this.createdBy;
    }
}
