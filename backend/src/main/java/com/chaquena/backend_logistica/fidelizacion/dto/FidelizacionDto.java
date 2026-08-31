package com.chaquena.backend_logistica.fidelizacion.dto;

import lombok.*;

import java.util.UUID;

/**
 * Progreso del cliente hacia la recompensa por N calificaciones.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FidelizacionDto {

    private UUID clienteId;
    private long calificacionesRealizadas;
    private int calificacionesRequeridas;
    private long calificacionesFaltantes;
    private long cuponesVigentes;
    private Integer puntosFidelidad;
    private String mensaje;
}
