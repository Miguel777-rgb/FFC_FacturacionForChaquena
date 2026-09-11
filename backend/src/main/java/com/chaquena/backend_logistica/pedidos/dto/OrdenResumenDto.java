package com.chaquena.backend_logistica.pedidos.dto;

import com.chaquena.backend_logistica.pedidos.domain.*;
import com.chaquena.backend_logistica.pedidos.service.MaquinaEstadosOrden;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** Fila de listado: lo justo para pintar una tabla o el mapa de mesas. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenResumenDto {

    private UUID id;
    private String clienteNombre;
    private TipoOrdenEnum tipoOrden;
    private CanalOrigenEnum canalOrigen;
    private EstadoOrdenEnum estado;
    private String mesaNumero;
    private UUID mesaId;
    private BigDecimal montoTotal;
    private TipoPagoEnum tipoPago;
    private Integer cantidadItems;
    private ZonedDateTime tiempoInicioGlobal;
    private Long minutosTranscurridos;

    /**
     * Misma razon que en OrdenResponseDto: el mapa de mesas y el tablero del
     * salon pintan botones sobre estas filas sin volver a pedir el detalle.
     */
    private List<EstadoOrdenEnum> transicionesPermitidas;
    private Boolean editable;

    /**
     * Los detalles pueden no estar cargados segun la consulta de origen; en ese
     * caso se devuelve null en lugar de forzar una carga diferida extra.
     */
    private static Integer contarItems(Orden o) {
        try {
            return o.getDetalles() != null ? o.getDetalles().size() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static OrdenResumenDto fromEntity(Orden o) {
        ZonedDateTime inicio = o.getTiempoInicioGlobal() != null
                ? o.getTiempoInicioGlobal()
                : o.getDateCreated();
        Long minutos = inicio != null
                ? java.time.Duration.between(inicio, ZonedDateTime.now()).toMinutes()
                : null;

        return OrdenResumenDto.builder()
                .id(o.getId())
                .clienteNombre(o.getCliente() != null
                        ? (o.getCliente().getNombres() + " " + o.getCliente().getApellidos()).trim()
                        : null)
                .tipoOrden(o.getTipoOrden())
                .canalOrigen(o.getCanalOrigen())
                .estado(o.getEstado())
                .mesaNumero(o.getMesaNumero())
                .mesaId(o.getMesa() != null ? o.getMesa().getId() : null)
                .montoTotal(o.getMontoTotal())
                .tipoPago(o.getTipoPago())
                .cantidadItems(contarItems(o))
                .tiempoInicioGlobal(inicio)
                .minutosTranscurridos(minutos)
                .transicionesPermitidas(
                        List.copyOf(MaquinaEstadosOrden.transicionesDesde(o.getEstado())))
                .editable(MaquinaEstadosOrden.esEditable(o.getEstado()))
                .build();
    }
}
