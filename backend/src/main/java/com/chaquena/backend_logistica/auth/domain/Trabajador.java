package com.chaquena.backend_logistica.auth.domain;

import com.chaquena.backend_logistica.personas.domain.Persona;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "trabajadores",
        uniqueConstraints = @UniqueConstraint(name = "uk_trabajadores_discord_user_id",
                columnNames = "discord_user_id"))
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

    /**
     * Cuenta de Discord (el "snowflake") atada a este trabajador con
     * {@code /vincular}.
     *
     * <p>Un bot de Discord solo recibe el snowflake de quien le escribe: nunca
     * el correo, que es privado y solo se obtiene por OAuth. Por eso la cuenta
     * no se reconoce sola y hay que atarla una vez con el correo ya dado de
     * alta. Es el equivalente de lo que hacia {@code celular} cuando el canal
     * era WhatsApp.
     *
     * <p>Vive aqui y no en {@code personas} porque una misma cuenta puede ser a
     * la vez la de un trabajador y la de un cliente: el mozo que en su dia libre
     * le pide delivery al bot de clientes. Con la columna en la tabla padre, la
     * ficha anonima que abre ese pedido y la vinculacion del trabajador chocaban
     * contra una sola restriccion. La unicidad se exige dentro de cada papel,
     * que es donde tiene sentido: dos trabajadores no pueden compartir cuenta.
     */
    @Column(name = "discord_user_id", length = 32)
    private String discordUserId;

    /** Un trabajador dado de alta esta activo mientras no se diga lo contrario. */
    @PrePersist
    public void prePersistTrabajador() {
        if (this.activo == null) this.activo = true;
    }
}