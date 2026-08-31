package com.chaquena.backend_logistica.delivery.dto;

import com.chaquena.backend_logistica.delivery.domain.Transportista;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransportistaResponseDto {

    private UUID id;
    private String dni;
    private String nombres;
    private String apellidos;
    private String nombreCompleto;
    private String celular;
    private String correo;
    private String empresaTransporte;
    private Boolean activo;
    private List<VehiculoResponseDto> vehiculos;

    public static TransportistaResponseDto fromEntity(Transportista t, boolean incluirVehiculos) {
        return TransportistaResponseDto.builder()
                .id(t.getId())
                .dni(t.getDni())
                .nombres(t.getNombres())
                .apellidos(t.getApellidos())
                .nombreCompleto((t.getNombres() + " " + t.getApellidos()).trim())
                .celular(t.getCelular())
                .correo(t.getCorreo())
                .empresaTransporte(t.getEmpresaTransporte())
                .activo(t.getActivo())
                .vehiculos(incluirVehiculos && t.getVehiculos() != null
                        ? t.getVehiculos().stream().map(VehiculoResponseDto::fromEntity).toList()
                        : null)
                .build();
    }
}
