package com.chaquena.backend_logistica.archivos.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Una imagen subida: la foto de un platillo o el logo del local.
 *
 * <p>La fila guarda lo que hace falta para servirla; los bytes viven en disco,
 * en {@code app.archivos.directorio}, con el id como nombre. El tipo es el que
 * se detecto en los primeros bytes, nunca el que declaro el navegador: el
 * archivo se sirve sin sesion desde el mismo origen que la aplicacion, y
 * aceptar un HTML que dice ser PNG seria dejar que cualquiera publique una
 * pagina dentro del sistema.
 *
 * <p>No se edita. Una foto nueva es otro archivo, y por eso cada uno se puede
 * cachear para siempre.
 */
@Entity
@Table(name = "archivos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Archivo {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tipo_contenido", nullable = false, length = 40)
    private String tipoContenido;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    /** Como se llamaba en el equipo de quien lo subio. Solo informativo. */
    @Column(name = "nombre_original", length = 255)
    private String nombreOriginal;

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
