package com.chaquena.backend_logistica.cocina.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpisCocinaDto {

    private long comandasEnCola;
    private long comandasEnPreparacion;
    private Double minutosPromedioRecepcion;
    private Double minutosPromedioCocina;
    private Double minutosPromedioDespacho;
    private Double minutosPromedioTotal;
    private long comandasFueraDeObjetivo;
    private Integer minutosObjetivo;
}
