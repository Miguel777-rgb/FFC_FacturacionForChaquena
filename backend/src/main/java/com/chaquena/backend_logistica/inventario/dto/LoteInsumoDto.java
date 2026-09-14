package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.EstadoVencimientoEnum;
import com.chaquena.backend_logistica.inventario.domain.LoteInsumo;
import com.chaquena.backend_logistica.inventario.service.ReglaLotes;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoteInsumoDto {

    private UUID id;
    private UUID insumoId;
    private UUID proveedorId;
    private String proveedorNombre;
    private BigDecimal cantidadInicial;
    private BigDecimal cantidadRestante;
    private BigDecimal costoUnitario;
    private LocalDate fechaVencimiento;
    private EstadoVencimientoEnum estado;

    /**
     * Lote que agrupo el stock que habia antes de que existieran los lotes
     * (migracion 06). No tiene proveedor, costo ni vencimiento, y no es un olvido.
     */
    private boolean inicial;

    private ZonedDateTime fechaIngreso;

    public static LoteInsumoDto fromEntity(LoteInsumo l, LocalDate hoy) {
        return LoteInsumoDto.builder()
                .id(l.getId())
                .insumoId(l.getInsumo() != null ? l.getInsumo().getId() : null)
                .proveedorId(l.getProveedor() != null ? l.getProveedor().getId() : null)
                .proveedorNombre(l.getProveedor() != null ? l.getProveedor().getNombre() : null)
                .cantidadInicial(l.getCantidadInicial())
                .cantidadRestante(l.getCantidadRestante())
                .costoUnitario(l.getCostoUnitario())
                .fechaVencimiento(l.getFechaVencimiento())
                .estado(ReglaLotes.estado(l.getFechaVencimiento(), hoy))
                .inicial(l.getControlOrigen() == null)
                .fechaIngreso(l.getDateCreated())
                .build();
    }
}
