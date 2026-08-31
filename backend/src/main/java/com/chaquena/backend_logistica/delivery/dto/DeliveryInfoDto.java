package com.chaquena.backend_logistica.delivery.dto;

import com.chaquena.backend_logistica.delivery.domain.OrdenDeliveryInfo;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import lombok.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryInfoDto {

    private Long id;
    private UUID ordenId;
    private EstadoOrdenEnum estadoOrden;
    private String direccionDelivery;
    private UUID transportistaId;
    private String transportistaNombre;
    private String transportistaTelefono;
    private String placaVehiculo;
    private Integer tiempoEstimadoMinutos;
    private ZonedDateTime horaDespacho;
    private ZonedDateTime horaEntrega;
    private Boolean otpVerificado;
    private Long minutosEnRuta;

    public static DeliveryInfoDto fromEntity(OrdenDeliveryInfo info, String placa) {
        Long enRuta = null;
        if (info.getHoraDespacho() != null) {
            ZonedDateTime fin = info.getHoraEntrega() != null ? info.getHoraEntrega() : ZonedDateTime.now();
            enRuta = Duration.between(info.getHoraDespacho(), fin).toMinutes();
        }

        return DeliveryInfoDto.builder()
                .id(info.getId())
                .ordenId(info.getOrden() != null ? info.getOrden().getId() : null)
                .estadoOrden(info.getOrden() != null ? info.getOrden().getEstado() : null)
                .direccionDelivery(info.getOrden() != null ? info.getOrden().getDireccionDelivery() : null)
                .transportistaId(info.getTransportista() != null ? info.getTransportista().getId() : null)
                .transportistaNombre(info.getTransportista() != null
                        ? (info.getTransportista().getNombres() + " "
                                + info.getTransportista().getApellidos()).trim()
                        : null)
                .transportistaTelefono(info.getTransportista() != null
                        ? info.getTransportista().getCelular() : null)
                .placaVehiculo(placa)
                .tiempoEstimadoMinutos(info.getTiempoEstimadoMinutos())
                .horaDespacho(info.getHoraDespacho())
                .horaEntrega(info.getHoraEntrega())
                .otpVerificado(info.getOtpVerificado())
                .minutosEnRuta(enRuta)
                .build();
    }
}
