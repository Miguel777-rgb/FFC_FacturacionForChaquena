package com.chaquena.backend_logistica.inventario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Que hizo la importacion, para decirlo en el aviso. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultadoImportacionCartaDto {

    private int seccionesCreadas;
    private int platillosCreados;
    private int platillosActualizados;
    private int platillosSinCambios;
    private int complementosCreados;
    private int complementosActualizados;
}
