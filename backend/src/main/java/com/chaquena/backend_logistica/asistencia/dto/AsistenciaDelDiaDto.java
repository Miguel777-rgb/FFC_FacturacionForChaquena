package com.chaquena.backend_logistica.asistencia.dto;

import com.chaquena.backend_logistica.asistencia.domain.EstadoAsistenciaEnum;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Una fila de la asistencia del dia: un turno con lo que paso, o una entrada
 * que no correspondia a ningun turno.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsistenciaDelDiaDto {

    private UUID trabajadorId;
    private String nombre;
    private String cargo;
    /** Nulo si entro sin tener turno ese dia. */
    private ZonedDateTime turnoInicio;
    private ZonedDateTime turnoFin;
    private ZonedDateTime entrada;
    private ZonedDateTime salida;
    private EstadoAsistenciaEnum estado;
    /** Cero si llego a tiempo o dentro de la tolerancia. */
    private long minutosTarde;
}
