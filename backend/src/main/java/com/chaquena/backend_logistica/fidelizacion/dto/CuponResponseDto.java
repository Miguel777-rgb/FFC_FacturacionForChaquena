package com.chaquena.backend_logistica.fidelizacion.dto;

import com.chaquena.backend_logistica.fidelizacion.domain.Cupon;
import com.chaquena.backend_logistica.fidelizacion.domain.EstadoCuponEnum;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuponResponseDto {

    private UUID id;
    private String codigo;
    private UUID clienteId;
    private String descripcion;
    private BigDecimal porcentajeDescuento;
    private BigDecimal montoDescuento;
    private ZonedDateTime fechaEmision;
    private ZonedDateTime fechaVencimiento;
    private EstadoCuponEnum estado;
    private boolean vigente;

    public static CuponResponseDto fromEntity(Cupon c) {
        return CuponResponseDto.builder()
                .id(c.getId())
                .codigo(c.getCodigo())
                .clienteId(c.getCliente() != null ? c.getCliente().getId() : null)
                .descripcion(c.getDescripcion())
                .porcentajeDescuento(c.getPorcentajeDescuento())
                .montoDescuento(c.getMontoDescuento())
                .fechaEmision(c.getFechaEmision())
                .fechaVencimiento(c.getFechaVencimiento())
                .estado(c.getEstado())
                .vigente(c.estaVigente())
                .build();
    }
}
