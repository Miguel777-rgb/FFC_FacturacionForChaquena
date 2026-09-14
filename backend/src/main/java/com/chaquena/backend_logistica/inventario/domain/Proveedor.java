package com.chaquena.backend_logistica.inventario.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * A quien se le compra. Lo usa el lote para decir de donde vino cada kilo.
 *
 * <p>No se borra: se da de baja. Un proveedor con el que ya no se trabaja
 * sigue siendo el origen de los lotes que entrego, y borrarlo dejaria esos
 * lotes sin saber de donde salieron.
 *
 * <p>El RUC admite nulo porque el mercado mayorista no siempre lo da; cuando
 * esta, es unico.
 */
@Entity
@Table(name = "proveedores",
        uniqueConstraints = @UniqueConstraint(name = "uk_proveedores_ruc", columnNames = "ruc"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "ruc", length = 11)
    private String ruc;

    /** Con quien se habla para hacer el pedido. */
    @Column(name = "contacto", length = 120)
    private String contacto;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "correo", length = 120)
    private String correo;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

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
        if (this.activo == null) this.activo = true;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}
