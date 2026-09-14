package com.chaquena.backend_logistica.fidelizacion.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Un escalon del programa de lealtad: desde cuantos puntos se alcanza y que
 * descuento da.
 *
 * <p>El orden de los niveles son sus puntos minimos, y por eso no hay una
 * columna de orden aparte: dos numeros que dijeran lo mismo terminarian
 * diciendo cosas distintas. Tampoco puede haber dos niveles desde los mismos
 * puntos, porque no habria forma de saber cual le toca a un cliente.
 */
@Entity
@Table(name = "niveles_lealtad", uniqueConstraints = {
        @UniqueConstraint(name = "uk_niveles_lealtad_nombre", columnNames = "nombre"),
        @UniqueConstraint(name = "uk_niveles_lealtad_puntos", columnNames = "puntos_minimos")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NivelLealtad {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @Column(name = "puntos_minimos", nullable = false)
    private Integer puntosMinimos;

    /** Cero es un nivel de reconocimiento sin rebaja, y es valido. */
    @Column(name = "porcentaje_descuento", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeDescuento;

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
        if (this.puntosMinimos == null) this.puntosMinimos = 0;
        if (this.porcentajeDescuento == null) this.porcentajeDescuento = BigDecimal.ZERO;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}
