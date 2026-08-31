package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.ComplementoPlatillo;
import com.chaquena.backend_logistica.inventario.domain.TipoComplementoEnum;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplementoResponseDto {

    private UUID id;
    private String nombre;
    private TipoComplementoEnum tipoComplemento;
    private BigDecimal precioAdicional;
    private UUID insumoAsociadoId;
    private String insumoAsociadoNombre;
    private Boolean activo;

    public static ComplementoResponseDto fromEntity(ComplementoPlatillo c) {
        return ComplementoResponseDto.builder()
                .id(c.getId())
                .nombre(c.getNombre())
                .tipoComplemento(c.getTipoComplemento())
                .precioAdicional(c.getPrecioAdicional())
                .insumoAsociadoId(c.getInsumoAsociado() != null ? c.getInsumoAsociado().getId() : null)
                .insumoAsociadoNombre(c.getInsumoAsociado() != null ? c.getInsumoAsociado().getNombre() : null)
                .activo(c.getActivo())
                .build();
    }
}
