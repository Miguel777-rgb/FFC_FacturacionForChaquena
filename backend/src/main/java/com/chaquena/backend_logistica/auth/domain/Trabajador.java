package com.chaquena.backend_logistica.auth.domain;

import com.chaquena.backend_logistica.personas.domain.Persona;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "trabajadores")
@PrimaryKeyJoinColumn(name = "persona_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Trabajador extends Persona {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_id", nullable = false)
    private Cargo cargo;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    /** Un trabajador dado de alta esta activo mientras no se diga lo contrario. */
    @PrePersist
    public void prePersistTrabajador() {
        if (this.activo == null) this.activo = true;
    }
}