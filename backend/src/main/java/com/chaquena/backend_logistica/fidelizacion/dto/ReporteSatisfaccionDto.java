package com.chaquena.backend_logistica.fidelizacion.dto;

import lombok.*;

import java.time.ZonedDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteSatisfaccionDto {

    private ZonedDateTime desde;
    private ZonedDateTime hasta;
    private long totalCalificaciones;
    private Double promedioAtencion;
    private Double promedioComida;
    private Double promedioLugar;
    private Double promedioGeneral;
}
