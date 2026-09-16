package com.chaquena.backend_logistica.asistencia.dto;

import lombok.*;

import java.time.ZonedDateTime;
import java.util.List;

/** Lo que necesita el boton de marcar: si estoy dentro, desde cuando, y mis turnos de la semana. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MiAsistenciaDto {

    private boolean dentro;
    /** La entrada abierta, si estoy dentro. */
    private ZonedDateTime entrada;
    /** Mis turnos de hoy a seis dias. */
    private List<TurnoDto> turnos;
}
