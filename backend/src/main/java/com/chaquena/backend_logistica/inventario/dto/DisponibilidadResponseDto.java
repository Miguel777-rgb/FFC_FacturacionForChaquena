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
public class DisponibilidadResponseDto {

    private boolean disponible;
    private List<Faltante> faltantes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Faltante {
        private UUID insumoId;
        private String insumoNombre;
        private String unidadMedida;
        private BigDecimal requerido;
        private BigDecimal disponible;
        private BigDecimal faltante;
    }
}
