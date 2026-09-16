package com.chaquena.backend_logistica.asistencia.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lo que hizo una persona en un rango: lo que vendio, como la calificaron y
 * como cumplio sus turnos. La nomina no esta: es otro sistema.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesempenoDto {

    private UUID trabajadorId;
    private String nombre;
    private String cargo;
    private LocalDate desde;
    private LocalDate hasta;

    /** Comandas tomadas como mozo. Cero para quien no toma comandas. */
    private long comandas;
    private BigDecimal vendido;
    private BigDecimal ticketPromedio;

    /** Promedio del puntaje de atencion de las comandas que tomo, de 1 a 5. Nulo sin calificaciones. */
    private BigDecimal satisfaccionAtencion;
    private long calificaciones;

    /** Turnos que ya empezaron en el rango. */
    private int turnos;
    private int asistidos;
    private int tardanzas;
    private int inasistencias;
    private long minutosTarde;
    private BigDecimal horasTrabajadas;
    /** Entradas que llevan tanto tiempo abiertas que son una salida que nadie marco. */
    private int salidasSinMarcar;
}
