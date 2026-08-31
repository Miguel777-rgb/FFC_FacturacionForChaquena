package com.chaquena.backend_logistica.pedidos.dto;

import com.chaquena.backend_logistica.pedidos.domain.OrdenDetalle;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenDetalleDto {

    private UUID id;
    private UUID platilloId;
    private String platilloNombre;
    private Integer cantidad;
    private BigDecimal precioVentaProducto;
    private BigDecimal montoSubtotal;
    private String excepcionesNota;
    private List<ComplementoDetalleDto> complementos;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ComplementoDetalleDto {
        private UUID complementoId;
        private String nombre;
        private Integer cantidad;
        private BigDecimal precioVentaComplemento;
    }

    public static OrdenDetalleDto fromEntity(OrdenDetalle d) {
        return OrdenDetalleDto.builder()
                .id(d.getId())
                .platilloId(d.getPlatillo() != null ? d.getPlatillo().getId() : null)
                .platilloNombre(d.getPlatillo() != null ? d.getPlatillo().getNombre() : null)
                .cantidad(d.getCantidad())
                .precioVentaProducto(d.getPrecioVentaProducto())
                .montoSubtotal(d.getMontoSubtotal())
                .excepcionesNota(d.getExcepcionesNota())
                .complementos(d.getComplementos() == null ? List.of()
                        : d.getComplementos().stream()
                                .map(c -> ComplementoDetalleDto.builder()
                                        .complementoId(c.getComplemento() != null
                                                ? c.getComplemento().getId() : null)
                                        .nombre(c.getComplemento() != null
                                                ? c.getComplemento().getNombre() : null)
                                        .cantidad(c.getCantidad())
                                        .precioVentaComplemento(c.getPrecioVentaComplemento())
                                        .build())
                                .toList())
                .build();
    }
}
