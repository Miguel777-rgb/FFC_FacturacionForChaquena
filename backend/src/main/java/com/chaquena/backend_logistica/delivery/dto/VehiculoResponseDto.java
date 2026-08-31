package com.chaquena.backend_logistica.delivery.dto;

import com.chaquena.backend_logistica.delivery.domain.TipoVehiculoEnum;
import com.chaquena.backend_logistica.delivery.domain.Vehiculo;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehiculoResponseDto {

    private UUID id;
    private UUID transportistaId;
    private TipoVehiculoEnum tipoVehiculo;
    private String placa;
    private String marcaModelo;
    private Boolean activo;

    public static VehiculoResponseDto fromEntity(Vehiculo v) {
        return VehiculoResponseDto.builder()
                .id(v.getId())
                .transportistaId(v.getTransportista() != null ? v.getTransportista().getId() : null)
                .tipoVehiculo(v.getTipoVehiculo())
                .placa(v.getPlaca())
                .marcaModelo(v.getMarcaModelo())
                .activo(v.getActivo())
                .build();
    }
}
