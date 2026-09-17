package com.chaquena.backend_logistica.inventario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Si el servidor puede leer cartas, y por que no cuando no puede. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadoLectorCartaDto {

    private boolean disponible;

    /** Nulo cuando esta disponible. */
    private String motivo;
}
