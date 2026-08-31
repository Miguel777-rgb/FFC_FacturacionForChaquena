package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.Insumo;
import com.chaquena.backend_logistica.inventario.domain.TipoInsumoEnum;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsumoResponseDto {

    private UUID id;
    private String nombre;
    private TipoInsumoEnum tipoInsumo;
    private String unidadMedida;
    private BigDecimal stockActual;
    private BigDecimal stockMinimo;
    private boolean bajoMinimo;

    public static InsumoResponseDto fromEntity(Insumo insumo) {
        BigDecimal actual = insumo.getStockActual() != null ? insumo.getStockActual() : BigDecimal.ZERO;
        BigDecimal minimo = insumo.getStockMinimo() != null ? insumo.getStockMinimo() : BigDecimal.ZERO;
        return InsumoResponseDto.builder()
                .id(insumo.getId())
                .nombre(insumo.getNombre())
                .tipoInsumo(insumo.getTipoInsumo())
                .unidadMedida(insumo.getUnidadMedida())
                .stockActual(actual)
                .stockMinimo(minimo)
                .bajoMinimo(actual.compareTo(minimo) <= 0)
                .build();
    }
}
