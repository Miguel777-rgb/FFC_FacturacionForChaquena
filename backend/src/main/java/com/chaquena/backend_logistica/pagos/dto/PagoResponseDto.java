package com.chaquena.backend_logistica.pagos.dto;

import com.chaquena.backend_logistica.pagos.domain.EstadoPagoEnum;
import com.chaquena.backend_logistica.pagos.domain.Pago;
import com.chaquena.backend_logistica.pedidos.domain.TipoPagoEnum;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoResponseDto {

    private UUID id;
    private UUID ordenId;
    private TipoPagoEnum tipoPago;
    private BigDecimal monto;
    private BigDecimal montoEntregado;
    private BigDecimal vuelto;
    private String referencia;
    private EstadoPagoEnum estado;
    private Boolean esFraudulento;
    private String observacion;
    private ZonedDateTime fecha;
    private String registradoPor;

    public static PagoResponseDto fromEntity(Pago p) {
        return PagoResponseDto.builder()
                .id(p.getId())
                .ordenId(p.getOrden() != null ? p.getOrden().getId() : null)
                .tipoPago(p.getTipoPago())
                .monto(p.getMonto())
                .montoEntregado(p.getMontoEntregado())
                .vuelto(p.getVuelto())
                .referencia(p.getReferencia())
                .estado(p.getEstado())
                .esFraudulento(p.getEsFraudulento())
                .observacion(p.getObservacion())
                .fecha(p.getDateCreated())
                .registradoPor(p.getCreatedBy())
                .build();
    }
}
