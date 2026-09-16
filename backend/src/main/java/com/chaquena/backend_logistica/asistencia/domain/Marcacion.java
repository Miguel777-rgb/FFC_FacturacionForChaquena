package com.chaquena.backend_logistica.asistencia.domain;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Una entrada y, cuando la hay, su salida. La marca cada trabajador para si
 * mismo desde su sesion; nadie marca por otro.
 *
 * <p>La salida admite nulo porque el nulo dice algo: la persona sigue dentro.
 */
@Entity
@Table(name = "marcaciones", indexes = @Index(name = "ix_marcaciones_entrada", columnList = "entrada"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Marcacion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trabajador_id", nullable = false)
    private Trabajador trabajador;

    @Column(name = "entrada", nullable = false)
    private ZonedDateTime entrada;

    @Column(name = "salida")
    private ZonedDateTime salida;

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
