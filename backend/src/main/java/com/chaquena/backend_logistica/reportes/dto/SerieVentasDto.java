package com.chaquena.backend_logistica.reportes.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Ventas repartidas en bloques consecutivos de tiempo.
 *
 * <p>Los bloques sin ninguna venta tambien vienen, con el total en cero. Es
 * deliberado: una grafica que solo dibuja las horas con movimiento aparenta un
 * servicio continuo y esconde justamente lo que se busca, que es donde el local
 * esta vacio.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SerieVentasDto {

    private ZonedDateTime desde;
    private ZonedDateTime hasta;
    private GranularidadEnum granularidad;
    private List<Punto> puntos;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Punto {
        /** Inicio del bloque, en la zona horaria con la que se pidio el reporte. */
        private ZonedDateTime inicio;
        private long comandas;
        private BigDecimal total;
    }
}
