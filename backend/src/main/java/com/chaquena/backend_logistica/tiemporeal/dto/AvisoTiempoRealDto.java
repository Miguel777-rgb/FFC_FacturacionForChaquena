package com.chaquena.backend_logistica.tiemporeal.dto;

import com.chaquena.backend_logistica.tiemporeal.domain.TemaEnum;

/**
 * Lo que viaja en cada evento {@code aviso} del stream: el tema que cambio.
 * La pantalla que lo escucha vuelve a pedir sus datos.
 */
public record AvisoTiempoRealDto(TemaEnum tema) {
}
