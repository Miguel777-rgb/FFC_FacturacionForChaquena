package com.chaquena.backend_logistica.fidelizacion.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Parametros operativos que administracion puede cambiar sin recompilar.
 * Fila unica con id = 1. El umbral N de calificaciones para el cupon es
 * paramétrico segun contexto.md, por eso no vive como constante en el codigo.
 */
@Entity
@Table(name = "configuracion_local")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionLocal {

    public static final Integer ID_UNICO = 1;

    /**
     * Con que arranca un local que todavia no ha tocado la configuracion. Son
     * constantes y no literales sueltos porque los usan dos sitios: la factoria
     * {@link #porDefecto()} y el callback de persistencia, que rellena lo que
     * llegue vacio para que ninguna columna quede en nulo.
     */
    public static final Integer CALIFICACIONES_PARA_CUPON_POR_DEFECTO = 5;
    public static final BigDecimal PORCENTAJE_DESCUENTO_CUPON_POR_DEFECTO = new BigDecimal("10.00");
    public static final Integer DIAS_VIGENCIA_CUPON_POR_DEFECTO = 30;
    public static final Integer MINUTOS_OBJETIVO_COCINA_POR_DEFECTO = 20;

    @Id
    @Column(name = "id")
    private Integer id;

    /** El "N" de la regla de fidelizacion: calificaciones para ganar un cupon. */
    @Column(name = "calificaciones_para_cupon", nullable = false)
    private Integer calificacionesParaCupon;

    @Column(name = "porcentaje_descuento_cupon", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeDescuentoCupon;

    @Column(name = "dias_vigencia_cupon", nullable = false)
    private Integer diasVigenciaCupon;

    /** Minutos objetivo de preparacion, para colorear la cola del KDS. */
    @Column(name = "minutos_objetivo_cocina", nullable = false)
    private Integer minutosObjetivoCocina;

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
        if (this.calificacionesParaCupon == null)
            this.calificacionesParaCupon = CALIFICACIONES_PARA_CUPON_POR_DEFECTO;
        if (this.porcentajeDescuentoCupon == null)
            this.porcentajeDescuentoCupon = PORCENTAJE_DESCUENTO_CUPON_POR_DEFECTO;
        if (this.diasVigenciaCupon == null)
            this.diasVigenciaCupon = DIAS_VIGENCIA_CUPON_POR_DEFECTO;
        if (this.minutosObjetivoCocina == null)
            this.minutosObjetivoCocina = MINUTOS_OBJETIVO_COCINA_POR_DEFECTO;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastDateModified = ZonedDateTime.now();
    }

    public static ConfiguracionLocal porDefecto() {
        return ConfiguracionLocal.builder()
                .id(ID_UNICO)
                .calificacionesParaCupon(CALIFICACIONES_PARA_CUPON_POR_DEFECTO)
                .porcentajeDescuentoCupon(PORCENTAJE_DESCUENTO_CUPON_POR_DEFECTO)
                .diasVigenciaCupon(DIAS_VIGENCIA_CUPON_POR_DEFECTO)
                .minutosObjetivoCocina(MINUTOS_OBJETIVO_COCINA_POR_DEFECTO)
                .createdBy("SYSTEM")
                .build();
    }
}
