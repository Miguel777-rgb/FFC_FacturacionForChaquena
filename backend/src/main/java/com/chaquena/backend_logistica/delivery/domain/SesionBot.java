package com.chaquena.backend_logistica.delivery.domain;

import com.chaquena.backend_logistica.clientes.domain.Cliente;
import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Conversacion de pedido a medio hacer con una persona concreta.
 *
 * <p>Sustituye a la antigua {@code whatsapp_sesiones}, que ataba la fila a un
 * numero de telefono. Ahora la llave es el par (canal, remitente): el
 * identificador lo pone el proveedor —telefono en WhatsApp, snowflake en
 * Discord— y el canal impide que dos proveedores distintos se pisen la sesion
 * si algun dia conviven.
 *
 * <p>El estado vive en base de datos y no en Redis, al contrario que el bot de
 * stock. Un carrito a medio llenar es trabajo del cliente: si el backend se
 * reinicia, perderlo significa hacerle repetir el pedido entero.
 */
@Entity
@Table(name = "sesiones_bot",
        uniqueConstraints = @UniqueConstraint(name = "uk_sesion_bot_canal_remitente",
                columnNames = {"canal", "remitente_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SesionBot {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 10)
    private CanalBot canal;

    /** Telefono en WhatsApp, snowflake del usuario en Discord. */
    @Column(name = "remitente_id", nullable = false, length = 64)
    private String remitenteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "paso_actual", nullable = false, length = 50)
    private String pasoActual;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "carrito_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> carritoJson;

    @Column(name = "expira_en", nullable = false)
    private ZonedDateTime expiraEn;

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
