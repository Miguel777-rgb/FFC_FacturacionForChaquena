package com.chaquena.backend_logistica.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Un permiso de un solo uso para volver a poner la contrasena.
 *
 * Lo que se guarda no es el token que viaja en el enlace, sino su huella
 * SHA-256. La diferencia importa: si alguien llega a leer esta tabla —una copia
 * de seguridad extraviada, un volcado de la base— no obtiene con que entrar,
 * porque de la huella no se vuelve al token. Es el mismo motivo por el que no se
 * guarda la contrasena sino su hash.
 *
 * Tampoco se guarda el correo al que se envio: ya esta en el trabajador, y
 * repetirlo aqui seria un dato personal mas que custodiar.
 */
@Entity
@Table(name = "tokens_recuperacion",
        uniqueConstraints = @UniqueConstraint(name = "uk_tokens_recuperacion_huella",
                columnNames = "huella"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenRecuperacion {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trabajador_id", nullable = false)
    private Trabajador trabajador;

    /** SHA-256 en hexadecimal del token que viaja en el enlace. */
    @Column(name = "huella", nullable = false, length = 64)
    private String huella;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    /**
     * Cuando se gasto. Nulo con sentido: el enlace todavia sirve. Se marca en
     * lugar de borrar la fila para poder responder «este enlace ya se uso» en
     * vez de «no existe», que es lo que le pasa a quien pulsa dos veces el
     * boton del correo.
     */
    @Column(name = "usado_en")
    private Instant usadoEn;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "date_created", nullable = false)
    private Instant dateCreated;

    @Column(name = "modified_by", nullable = false, length = 100)
    private String modifiedBy;

    @Column(name = "last_date_modified", nullable = false)
    private Instant lastDateModified;

    @PrePersist
    void alCrear() {
        Instant ahora = Instant.now();
        this.dateCreated = ahora;
        this.lastDateModified = ahora;
        // Nadie ha iniciado sesion cuando se pide recuperar la contrasena: la
        // fila la escribe el sistema a peticion de quien dice ser el dueno.
        if (this.createdBy == null) this.createdBy = "RECUPERACION";
        if (this.modifiedBy == null) this.modifiedBy = this.createdBy;
    }

    @PreUpdate
    void alModificar() {
        this.lastDateModified = Instant.now();
        if (this.modifiedBy == null) this.modifiedBy = this.createdBy;
    }

    public boolean vigente(Instant ahora) {
        return usadoEn == null && expiraEn.isAfter(ahora);
    }
}
