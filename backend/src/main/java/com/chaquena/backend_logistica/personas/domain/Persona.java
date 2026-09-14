package com.chaquena.backend_logistica.personas.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "personas")
@Inheritance(strategy = InheritanceType.JOINED) // Herencia física en PostgreSQL (Tabla Padre)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "dni", nullable = false, unique = true, length = 15)
    private String dni;

    @Column(name = "nombres", nullable = false, length = 100)
    private String nombres;

    @Column(name = "apellidos", nullable = false, length = 100)
    private String apellidos;

    @Column(name = "correo", unique = true, length = 100)
    private String correo;

    @Column(name = "celular", unique = true, length = 20)
    private String celular;

    // La cuenta de Discord no vive aqui sino en cada hija (Trabajador y
    // Cliente): una misma cuenta puede ser las dos cosas a la vez.

    // Campo de control para la regla de negocio de restricción de 15 días
    @Column(name = "fecha_ultimo_cambio_correo")
    private ZonedDateTime fechaUltimoCambioCorreo;

    // -----------------------------------------------------
    // Campos de Auditoría Obligatorios
    // -----------------------------------------------------
    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "date_created", nullable = false)
    private ZonedDateTime dateCreated;

    /**
     * Quien toco la fila por ultima vez. En una fila recien creada es el mismo
     * que la creo: "nadie la ha modificado" se dice con created_by == modified_by
     * y no con un nulo, que obligaria a cada consulta de auditoria a distinguir
     * entre "sin modificar" y "se perdio el dato".
     */
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
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}