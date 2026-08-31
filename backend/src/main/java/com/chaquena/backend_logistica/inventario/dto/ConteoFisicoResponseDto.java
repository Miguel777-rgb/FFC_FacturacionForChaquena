package com.chaquena.backend_logistica.inventario.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConteoFisicoResponseDto {

    private int insumosContados;
    private int insumosAjustados;
    private List<Descuadre> descuadres;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Descuadre {
        private UUID insumoId;
        private String insumoNombre;
        private String unidadMedida;
        private BigDecimal stockSistema;
        private BigDecimal stockContado;
        /** Positivo = sobra respecto al sistema; negativo = falta. */
        private BigDecimal diferencia;
    }
}
