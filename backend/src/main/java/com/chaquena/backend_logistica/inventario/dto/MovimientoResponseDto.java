package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.ControlInsumo;
import com.chaquena.backend_logistica.inventario.domain.TipoControlInsumoEnum;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

/** Linea del kardex. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoResponseDto {

    private UUID id;
    private UUID insumoId;
    private String insumoNombre;
    private String unidadMedida;
    private TipoControlInsumoEnum tipoControl;
    private BigDecimal cantidad;
    private BigDecimal stockAnterior;
    private BigDecimal stockNuevo;
    private String motivoObservacion;
    private UUID trabajadorId;
    private String registradoPor;
    private ZonedDateTime fecha;

    public static MovimientoResponseDto fromEntity(ControlInsumo c) {
        return MovimientoResponseDto.builder()
                .id(c.getId())
                .insumoId(c.getInsumo() != null ? c.getInsumo().getId() : null)
                .insumoNombre(c.getInsumo() != null ? c.getInsumo().getNombre() : null)
                .unidadMedida(c.getInsumo() != null ? c.getInsumo().getUnidadMedida() : null)
                .tipoControl(c.getTipoControl())
                .cantidad(c.getCantidad())
                .stockAnterior(c.getStockAnterior())
                .stockNuevo(c.getStockNuevo())
                .motivoObservacion(c.getMotivoObservacion())
                .trabajadorId(c.getTrabajadorId())
                .registradoPor(c.getCreatedBy())
                .fecha(c.getDateCreated())
                .build();
    }
}
