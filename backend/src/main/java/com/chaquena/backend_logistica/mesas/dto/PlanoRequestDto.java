package com.chaquena.backend_logistica.mesas.dto;

import com.chaquena.backend_logistica.mesas.domain.FormaMesaEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * Las mesas que se movieron en el plano, todas de una vez: una mesa movida
 * sola podria pisar el sitio que otra todavia no ha dejado.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanoRequestDto {

    @NotEmpty(message = "El plano no trae ninguna mesa")
    @Valid
    private List<PosicionMesa> mesas;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PosicionMesa {

        @NotNull(message = "Cada posicion necesita su mesa")
        private UUID id;

        @NotNull(message = "Falta la columna")
        private Integer columna;

        @NotNull(message = "Falta la fila")
        private Integer fila;

        @NotNull(message = "Falta el ancho")
        private Integer ancho;

        @NotNull(message = "Falta el alto")
        private Integer alto;

        /** Sin forma se conserva la que tenia. */
        private FormaMesaEnum forma;
    }
}
