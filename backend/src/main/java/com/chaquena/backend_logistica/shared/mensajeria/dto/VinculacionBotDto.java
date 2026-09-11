package com.chaquena.backend_logistica.shared.mensajeria.dto;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import lombok.*;

import java.util.UUID;

/**
 * Una cuenta del proveedor de mensajeria atada a un trabajador.
 *
 * <p>Responde a la pregunta que hoy no tiene pantalla: quien puede darle
 * ordenes al bot interno. El identificador del proveedor se devuelve entero
 * porque es lo que hay que cotejar contra el servidor de Discord cuando una
 * vinculacion parece equivocada, y no es un secreto: es publico para cualquiera
 * que comparta servidor con esa persona.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VinculacionBotDto {

    private UUID trabajadorId;
    private String username;
    private String nombreCompleto;
    private String cargoNombre;
    private String correo;
    private String discordUserId;
    private Boolean activo;

    public static VinculacionBotDto fromEntity(Trabajador t) {
        return VinculacionBotDto.builder()
                .trabajadorId(t.getId())
                .username(t.getUsername())
                .nombreCompleto((t.getNombres() + " " + t.getApellidos()).trim())
                .cargoNombre(t.getCargo() != null ? t.getCargo().getNombre() : null)
                .correo(t.getCorreo())
                .discordUserId(t.getDiscordUserId())
                .activo(t.getActivo())
                .build();
    }
}
