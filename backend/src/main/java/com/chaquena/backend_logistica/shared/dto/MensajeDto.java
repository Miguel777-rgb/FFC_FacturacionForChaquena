package com.chaquena.backend_logistica.shared.dto;

import lombok.*;

/**
 * Respuesta simple para operaciones que no devuelven un recurso.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MensajeDto {

    private String mensaje;

    public static MensajeDto de(String mensaje) {
        return MensajeDto.builder().mensaje(mensaje).build();
    }
}
