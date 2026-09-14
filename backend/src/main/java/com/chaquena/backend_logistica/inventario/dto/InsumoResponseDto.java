package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.Insumo;
import com.chaquena.backend_logistica.inventario.domain.TipoInsumoEnum;
import com.chaquena.backend_logistica.inventario.service.ReglaLotes;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** El vencimiento mas cercano de lo que queda. Nulo si nada de lo que queda tiene fecha. */
    private LocalDate proximoVencimiento;

    /** Lo que queda y ya vencio. */
    private BigDecimal cantidadVencida;

    /** Lo que queda y vence hoy o dentro de los dias de aviso. */
    private BigDecimal cantidadPorVencer;

    /** Valor de lo que queda con costo conocido, en soles. */
    private BigDecimal valorStock;

    /** Lo que queda sin costo registrado. No entra en el valor: contarlo como cero lo abarataria. */
    private BigDecimal cantidadSinCosto;

    public static InsumoResponseDto fromEntity(Insumo insumo) {
        return fromEntity(insumo, null);
    }

    public static InsumoResponseDto fromEntity(Insumo insumo, ReglaLotes.Resumen lotes) {
        BigDecimal actual = insumo.getStockActual() != null ? insumo.getStockActual() : BigDecimal.ZERO;
        BigDecimal minimo = insumo.getStockMinimo() != null ? insumo.getStockMinimo() : BigDecimal.ZERO;
        BigDecimal conCosto = lotes != null ? lotes.cantidadConCosto() : BigDecimal.ZERO;

        return InsumoResponseDto.builder()
                .id(insumo.getId())
                .nombre(insumo.getNombre())
                .tipoInsumo(insumo.getTipoInsumo())
                .unidadMedida(insumo.getUnidadMedida())
                .stockActual(actual)
                .stockMinimo(minimo)
                .bajoMinimo(actual.compareTo(minimo) <= 0)
                .proximoVencimiento(lotes != null ? lotes.proximoVencimiento() : null)
                .cantidadVencida(lotes != null ? lotes.cantidadVencida() : BigDecimal.ZERO)
                .cantidadPorVencer(lotes != null ? lotes.cantidadPorVencer() : BigDecimal.ZERO)
                .valorStock(lotes != null ? lotes.valor() : BigDecimal.ZERO)
                .cantidadSinCosto(actual.subtract(conCosto).max(BigDecimal.ZERO))
                .build();
    }
}
