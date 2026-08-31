package com.chaquena.backend_logistica.pedidos.dto;

import com.chaquena.backend_logistica.pedidos.domain.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenResponseDto {

    private UUID id;
    private UUID clienteId;
    private String clienteNombre;
    private UUID mozoId;
    private TipoOrdenEnum tipoOrden;
    private CanalOrigenEnum canalOrigen;
    private String mesaNumero;
    private UUID mesaId;
    private String direccionDelivery;
    private String codigoOtpEntrega;
    private Integer scoringRiesgoOrden;
    private BigDecimal montoSubtotal;
    private BigDecimal montoDescuento;
    private BigDecimal montoTotal;
    private TipoPagoEnum tipoPago;
    private EstadoOrdenEnum estado;
    private String promocionNombre;
    private String cuponCodigo;
    private String motivoCancelacion;

    private Boolean flagCierreRecepcion;
    private Boolean flagCierrePlatillo;
    private Boolean flagCierreDespacho;

    private ZonedDateTime tiempoInicioGlobal;
    private ZonedDateTime tiempoCierreRecepcion;
    private ZonedDateTime tiempoInicioCocina;
    /** Minutos que cocina prometio al tomar la comanda. Nulo si aun no la tomo. */
    private Integer tiempoEstimadoCocinaMinutos;
    private ZonedDateTime tiempoCierrePlatillo;
    private ZonedDateTime tiempoCierreDespacho;
    private ZonedDateTime tiempoFinGlobal;

    private List<OrdenDetalleDto> detalles;

    /**
     * El OTP solo viaja al crear la comanda y hacia el cliente; en las lecturas
     * generales se omite para que no quede a la vista del repartidor.
     */
    public static OrdenResponseDto fromEntity(Orden o, boolean incluirOtp) {
        return OrdenResponseDto.builder()
                .id(o.getId())
                .clienteId(o.getCliente() != null ? o.getCliente().getId() : null)
                .clienteNombre(o.getCliente() != null
                        ? (o.getCliente().getNombres() + " " + o.getCliente().getApellidos()).trim()
                        : null)
                .mozoId(o.getMozoId())
                .tipoOrden(o.getTipoOrden())
                .canalOrigen(o.getCanalOrigen())
                .mesaNumero(o.getMesaNumero())
                .mesaId(o.getMesa() != null ? o.getMesa().getId() : null)
                .direccionDelivery(o.getDireccionDelivery())
                .codigoOtpEntrega(incluirOtp ? o.getCodigoOtpEntrega() : null)
                .scoringRiesgoOrden(o.getScoringRiesgoOrden())
                .montoSubtotal(o.getMontoSubtotal())
                .montoDescuento(o.getMontoDescuento())
                .montoTotal(o.getMontoTotal())
                .tipoPago(o.getTipoPago())
                .estado(o.getEstado())
                .promocionNombre(o.getPromocion() != null ? o.getPromocion().getNombre() : null)
                .cuponCodigo(o.getCuponCodigo())
                .motivoCancelacion(o.getMotivoCancelacion())
                .flagCierreRecepcion(o.getFlagCierreRecepcion())
                .flagCierrePlatillo(o.getFlagCierrePlatillo())
                .flagCierreDespacho(o.getFlagCierreDespacho())
                .tiempoInicioGlobal(o.getTiempoInicioGlobal())
                .tiempoCierreRecepcion(o.getTiempoCierreRecepcion())
                .tiempoInicioCocina(o.getTiempoInicioCocina())
                .tiempoEstimadoCocinaMinutos(o.getTiempoEstimadoCocinaMinutos())
                .tiempoCierrePlatillo(o.getTiempoCierrePlatillo())
                .tiempoCierreDespacho(o.getTiempoCierreDespacho())
                .tiempoFinGlobal(o.getTiempoFinGlobal())
                .detalles(o.getDetalles() == null ? List.of()
                        : o.getDetalles().stream().map(OrdenDetalleDto::fromEntity).toList())
                .build();
    }
}
