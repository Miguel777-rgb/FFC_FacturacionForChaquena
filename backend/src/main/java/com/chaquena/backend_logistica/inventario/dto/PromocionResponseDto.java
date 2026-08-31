package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.Promocion;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromocionResponseDto {

    private UUID id;
    private String nombre;
    private String descripcion;
    private BigDecimal porcentajeDescuento;
    private BigDecimal montoDescuento;
    private Boolean requiereInsumoExtra;
    private UUID insumoExtraId;
    private String insumoExtraNombre;
    private ZonedDateTime fechaInicio;
    private ZonedDateTime fechaFin;
    private Boolean activa;

    /** false cuando la promo requiere un insumo extra que ya no hay en stock. */
    private Boolean aplicable;
    private String motivoNoAplicable;

    public static PromocionResponseDto fromEntity(Promocion p) {
        return PromocionResponseDto.builder()
                .id(p.getId())
                .nombre(p.getNombre())
                .descripcion(p.getDescripcion())
                .porcentajeDescuento(p.getPorcentajeDescuento())
                .montoDescuento(p.getMontoDescuento())
                .requiereInsumoExtra(p.getRequiereInsumoExtra())
                .insumoExtraId(p.getInsumoExtra() != null ? p.getInsumoExtra().getId() : null)
                .insumoExtraNombre(p.getInsumoExtra() != null ? p.getInsumoExtra().getNombre() : null)
                .fechaInicio(p.getFechaInicio())
                .fechaFin(p.getFechaFin())
                .activa(p.getActiva())
                .build();
    }
}
