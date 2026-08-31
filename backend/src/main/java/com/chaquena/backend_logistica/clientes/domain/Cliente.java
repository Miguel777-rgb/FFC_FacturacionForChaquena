package com.chaquena.backend_logistica.clientes.domain;

import com.chaquena.backend_logistica.personas.domain.Persona;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "clientes")
@PrimaryKeyJoinColumn(name = "persona_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Cliente extends Persona {

    /**
     * Direccion a la que suele pedir delivery. La orden guarda igual su propia
     * direccion, porque un pedido puntual puede ir a otro lado.
     */
    @Column(name = "direccion_habitual", columnDefinition = "TEXT")
    private String direccionHabitual;

    @Column(name = "tipo_cliente", length = 50)
    private String tipoCliente; // 'CF' (Cliente Frecuente), 'CT 20%', 'CD 10%'

    @Column(name = "puntos_fidelidad", nullable = false)
    private Integer puntosFidelidad;

    @Column(name = "score_fraude", nullable = false)
    private Integer scoreFraude;

    @Column(name = "bloqueado_por_fraude", nullable = false)
    private Boolean bloqueadoPorFraude;

    /**
     * Valores de partida de un cliente nuevo. Se suman a los que pone
     * {@link com.chaquena.backend_logistica.personas.domain.Persona}: JPA
     * invoca primero el callback del padre y despues el del hijo.
     */
    @PrePersist
    public void prePersistCliente() {
        if (this.puntosFidelidad == null) this.puntosFidelidad = 0;
        if (this.scoreFraude == null) this.scoreFraude = 0;
        if (this.bloqueadoPorFraude == null) this.bloqueadoPorFraude = false;
    }

    /**
     * Por donde se le puede escribir con el bot de clientes.
     *
     * <p>Se prefiere la cuenta de Discord porque es el canal en servicio; el
     * celular queda como resto de la epoca de WhatsApp y sirve para las fichas
     * antiguas si algun dia se vuelve a encender ese adaptador. Devuelve nulo
     * cuando no hay ninguno, que es el caso de un cliente dado de alta a mano
     * en el POS: a ese no se le puede avisar por chat y quien llame a este
     * metodo tiene que contar con ello.
     */
    public String identificadorDeBot() {
        if (getDiscordUserId() != null && !getDiscordUserId().isBlank()) {
            return getDiscordUserId();
        }
        return getCelular();
    }
}