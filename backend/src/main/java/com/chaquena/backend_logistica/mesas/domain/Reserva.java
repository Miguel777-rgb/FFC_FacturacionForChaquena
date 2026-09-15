package com.chaquena.backend_logistica.mesas.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Una mesa apartada para alguien, de tal hora a tal hora.
 *
 * <p>Sustituye a las dos columnas sueltas que tenia la mesa
 * ({@code reservada_a_nombre_de} y {@code reservada_para}), que solo admitian
 * una reserva por mesa y la perdian al liberarla: no habia forma de ver la
 * agenda de la noche ni de saber quien no llego.
 *
 * <p>La mesa no guarda que esta reservada. Se ve reservada mientras una reserva
 * activa la aparta (ver {@code ReglaReservas}); asi una reserva que se cancela
 * o que termina no deja la mesa bloqueada a la espera de que alguien la libere.
 */
@Entity
@Table(name = "reservas", indexes = @Index(name = "ix_reservas_inicio", columnList = "inicio"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id", nullable = false)
    private Mesa mesa;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    /** Para avisar si la mesa no va a estar lista. Opcional: hay quien reserva en persona. */
    @Column(name = "celular", length = 20)
    private String celular;

    @Column(name = "personas", nullable = false)
    private Integer personas;

    @Column(name = "inicio", nullable = false)
    private ZonedDateTime inicio;

    @Column(name = "duracion_minutos", nullable = false)
    private Integer duracionMinutos;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoReservaEnum estado;

    @Column(name = "nota", columnDefinition = "TEXT")
    private String nota;

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
        if (this.estado == null) this.estado = EstadoReservaEnum.PENDIENTE;
        if (this.duracionMinutos == null) this.duracionMinutos = 90;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}
