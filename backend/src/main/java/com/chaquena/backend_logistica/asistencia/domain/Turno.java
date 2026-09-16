package com.chaquena.backend_logistica.asistencia.domain;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Cuando le toca trabajar a alguien: un dia, de tal hora a tal hora.
 *
 * <p>Las horas van sin zona porque son las del local. Si la de fin es igual o
 * anterior a la de inicio, el turno termina al dia siguiente: el turno de la
 * noche que cierra a las dos no es un turno de cero horas.
 *
 * <p>Es lo que se esperaba; lo que paso de verdad esta en {@link Marcacion}. La
 * tardanza y la inasistencia salen de comparar los dos, nunca se guardan.
 */
@Entity
@Table(name = "turnos", indexes = @Index(name = "ix_turnos_fecha", columnList = "fecha"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trabajador_id", nullable = false)
    private Trabajador trabajador;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "inicio", nullable = false)
    private LocalTime inicio;

    @Column(name = "fin", nullable = false)
    private LocalTime fin;

    @Column(name = "nota", length = 200)
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
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}
