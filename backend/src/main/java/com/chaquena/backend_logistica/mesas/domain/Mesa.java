package com.chaquena.backend_logistica.mesas.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Mesa fisica del salon. El diseno original solo guardaba mesa_numero como
 * texto suelto dentro de la orden, con lo que no habia donde representar una
 * mesa libre o reservada: el mapa de mesas del POS necesita esta entidad.
 */
@Entity
@Table(name = "mesas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "numero", nullable = false, unique = true, length = 10)
    private String numero;

    @Column(name = "zona", length = 50)
    private String zona; // Ej: "Salon principal", "Terraza", "Segundo piso"

    @Column(name = "capacidad")
    private Integer capacidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoMesaEnum estado;

    @Column(name = "reservada_a_nombre_de", length = 120)
    private String reservadaANombreDe;

    @Column(name = "reservada_para")
    private ZonedDateTime reservadaPara;

    @Column(name = "activa", nullable = false)
    private Boolean activa;

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
        if (this.estado == null) this.estado = EstadoMesaEnum.LIBRE;
        if (this.activa == null) this.activa = true;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}
