package com.chaquena.backend_logistica.cocina.dto;

import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tarjeta de la pantalla de cocina. Lleva el tiempo transcurrido y si ya paso
 * el objetivo, para que la vista pueda ordenar y colorear sin recalcular.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComandaKdsDto {

    private UUID ordenId;
    private String correlativo;
    private TipoOrdenEnum tipoOrden;
    private EstadoOrdenEnum estado;
    private String mesaNumero;
    private ZonedDateTime recibida;
    private long minutosEnCola;
    private boolean fueraDeObjetivo;
    /** Minutos prometidos por cocina, si ya fijo un tiempo. */
    private Integer tiempoEstimadoCocinaMinutos;
    private Boolean flagCierrePlatillo;
    private List<LineaKds> lineas;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LineaKds {
        private UUID detalleId;
        private Integer cantidad;
        private String platillo;
        private List<String> complementos;
        private String nota;
    }
}
