package com.chaquena.backend_logistica.inventario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Lo que se leyo de una foto de la carta. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LecturaCartaDto {

    private List<SeccionLeidaDto> secciones;

    private List<ComplementoLeidoDto> complementos;

    /** Cuantos productos quedaron dudosos, para decirlo antes de abrir la tabla. */
    private int dudosos;
}
