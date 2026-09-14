package com.chaquena.backend_logistica.local.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * El horario de un dia de la semana. Hay siete filas, una por dia.
 *
 * <p>{@code cerrado} y las horas no dicen lo mismo. Cerrado es una decision
 * («los lunes no abrimos»); las horas en nulo con {@code cerrado = false}
 * significan que nadie las definio todavia. Poner 00:00 en su lugar haria
 * pasar un dato que falta por un local que abre a medianoche.
 *
 * <p>La hora de cierre puede ser anterior a la de apertura: un local que abre
 * a las 18:00 y cierra a la 01:00 cruza la medianoche.
 */
@Entity
@Table(name = "horarios_local",
        uniqueConstraints = @UniqueConstraint(name = "uk_horarios_local_dia", columnNames = "dia"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HorarioLocal {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia", nullable = false, length = 10)
    private DayOfWeek dia;

    @Column(name = "abre")
    private LocalTime abre;

    @Column(name = "cierra")
    private LocalTime cierra;

    @Column(name = "cerrado", nullable = false)
    private Boolean cerrado;

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
        if (this.cerrado == null) this.cerrado = false;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }

    public static HorarioLocal sinDefinir(DayOfWeek dia) {
        return HorarioLocal.builder().dia(dia).cerrado(false).createdBy("SYSTEM").build();
    }
}
