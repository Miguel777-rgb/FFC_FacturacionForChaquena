package com.chaquena.backend_logistica.local.domain;

import com.chaquena.backend_logistica.archivos.domain.Archivo;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Quien es el local: el nombre con el que se presenta, su RUC, donde esta y
 * cuanto IGV llevan sus precios.
 *
 * <p>Fila unica con id = 1, igual que {@code ConfiguracionLocal}. Van en
 * tablas separadas porque cambian por motivos distintos: la configuracion
 * gobierna reglas del turno (cupones, objetivo de cocina) y esto es la
 * identidad que ira en cada comprobante.
 *
 * <p>Los textos admiten nulo: un local recien instalado todavia no los tiene y
 * rellenarlos con algo seria inventar un RUC. El IGV no, porque las comandas
 * lo congelan al crearse y un nulo no se puede desglosar.
 */
@Entity
@Table(name = "datos_local")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatosLocal {

    public static final Integer ID_UNICO = 1;

    /** La tasa general del Peru. Los precios de la carta ya la incluyen. */
    public static final BigDecimal PORCENTAJE_IGV_POR_DEFECTO = new BigDecimal("18.00");

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "nombre_comercial", length = 120)
    private String nombreComercial;

    @Column(name = "ruc", length = 11)
    private String ruc;

    @Column(name = "direccion", columnDefinition = "TEXT")
    private String direccion;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "correo", length = 120)
    private String correo;

    @Column(name = "porcentaje_igv", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeIgv;

    /** El logo que muestran todas las pantallas. Antes vivia en cada dispositivo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_id")
    private Archivo logo;

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
        if (this.porcentajeIgv == null) this.porcentajeIgv = PORCENTAJE_IGV_POR_DEFECTO;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }

    public static DatosLocal porDefecto() {
        return DatosLocal.builder()
                .id(ID_UNICO)
                .porcentajeIgv(PORCENTAJE_IGV_POR_DEFECTO)
                .createdBy("SYSTEM")
                .build();
    }
}
