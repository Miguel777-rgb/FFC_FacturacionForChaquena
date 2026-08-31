package com.chaquena.backend_logistica.pedidos.dto;

import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** Payload plano para la impresora de comandas de cocina. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketCocinaDto {

    private UUID ordenId;
    private String correlativo;
    private TipoOrdenEnum tipoOrden;
    private String mesaNumero;
    private String clienteNombre;
    private ZonedDateTime emitido;
    private List<LineaTicket> lineas;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LineaTicket {
        private Integer cantidad;
        private String platillo;
        private List<String> complementos;
        private String nota;
    }
}
