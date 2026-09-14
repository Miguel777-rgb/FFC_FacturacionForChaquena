package com.chaquena.backend_logistica.fidelizacion.dto;

import lombok.*;

import java.util.UUID;

/**
 * Progreso del cliente hacia la recompensa por N calificaciones y hacia el
 * siguiente nivel de lealtad.
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

    /** El nivel que le toca por sus puntos. Nulo si el local no definio niveles o no llega al primero. */
    private NivelLealtadDto nivelActual;

    /** El siguiente escalon. Nulo si ya esta en el mas alto. */
    private NivelLealtadDto nivelSiguiente;

    /** Puntos que le faltan para el siguiente nivel. Nulo cuando no hay siguiente. */
    private Integer puntosParaSiguiente;
}
