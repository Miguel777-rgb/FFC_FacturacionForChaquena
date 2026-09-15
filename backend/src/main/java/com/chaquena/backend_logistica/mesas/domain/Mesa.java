package com.chaquena.backend_logistica.mesas.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Mesa fisica del salon. El diseno original solo guardaba mesa_numero como
 * texto suelto dentro de la orden, con lo que no habia donde representar una
 * mesa libre o reservada: el mapa de mesas del POS necesita esta entidad.
 *
 * <p>Su lugar en el plano va en celdas de la rejilla de su zona. Las cinco
 * columnas del plano llevan un valor por defecto en la base porque se agregan a
 * una tabla que ya tiene filas: sin el, Postgres no acepta un NOT NULL nuevo.
 *
 * <p>Las reservas viven en {@link Reserva}. El estado guardado solo dice si la
 * mesa esta libre, ocupada por una comanda o inhabilitada; "reservada" se
 * calcula con la agenda.
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

    @Column(name = "activa", nullable = false)
    private Boolean activa;

    /** Primera columna que ocupa en la rejilla de su zona, desde cero. */
    @ColumnDefault("0")
    @Column(name = "columna", nullable = false)
    private Integer columna;

    /** Primera fila que ocupa, desde cero. */
    @ColumnDefault("0")
    @Column(name = "fila", nullable = false)
    private Integer fila;

    /** Cuantas celdas ocupa a lo ancho. */
    @ColumnDefault("2")
    @Column(name = "ancho", nullable = false)
    private Integer ancho;

    /** Cuantas celdas ocupa a lo alto. */
    @ColumnDefault("2")
    @Column(name = "alto", nullable = false)
    private Integer alto;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'CUADRADA'")
    @Column(name = "forma", nullable = false, length = 20)
    private FormaMesaEnum forma;

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
        if (this.columna == null) this.columna = 0;
        if (this.fila == null) this.fila = 0;
        if (this.ancho == null) this.ancho = 2;
        if (this.alto == null) this.alto = 2;
        if (this.forma == null) this.forma = FormaMesaEnum.CUADRADA;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }
}
