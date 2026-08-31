package com.chaquena.backend_logistica.delivery.dto;

import com.chaquena.backend_logistica.delivery.domain.TipoVehiculoEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehiculoRequestDto {

    @NotNull(message = "El tipo de vehiculo es obligatorio")
    private TipoVehiculoEnum tipoVehiculo;

    @NotBlank(message = "La placa es obligatoria: sin ella no se puede despachar")
    @Size(max = 15, message = "La placa no puede exceder 15 caracteres")
    private String placa;

    @Size(max = 100, message = "La marca y modelo no pueden exceder 100 caracteres")
    private String marcaModelo;

    private Boolean activo;
}
