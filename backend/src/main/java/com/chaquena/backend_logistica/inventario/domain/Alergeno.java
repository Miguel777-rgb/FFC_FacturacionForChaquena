package com.chaquena.backend_logistica.inventario.domain;

import jakarta.persistence.*;
import lombok.*;

import java.text.Collator;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Un alergeno de la carta: lo que un comensal pregunta antes de pedir.
 *
 * <p>Es un catalogo y no un enum porque la lista la decide el local. Se
 * siembran los catorce de declaracion habitual y el local agrega los que su
 * cocina necesite. Se dan de baja en vez de borrarse, igual que los
 * proveedores: uno que ya no se ofrece sigue marcado en los platillos que lo
 * tenian, porque el plato no dejo de llevarlo.
 */
@Entity
@Table(name = "alergenos", uniqueConstraints = @UniqueConstraint(name = "uk_alergenos_nombre", columnNames = "nombre"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alergeno {

    private static final Locale ESPANOL = Locale.of("es");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

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

    /** Los nombres en orden alfabetico espanol, como se leen en la carta, el POS y la cocina. */
    public static List<String> nombresDe(Collection<Alergeno> alergenos) {
        if (alergenos == null) {
            return List.of();
        }
        return alergenos.stream().map(Alergeno::getNombre).sorted(Collator.getInstance(ESPANOL)).toList();
    }
}
