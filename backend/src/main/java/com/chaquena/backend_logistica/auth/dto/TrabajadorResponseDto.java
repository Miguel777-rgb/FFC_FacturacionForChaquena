package com.chaquena.backend_logistica.auth.dto;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrabajadorResponseDto {

    private UUID id;
    private String dni;
    private String nombres;
    private String apellidos;
    private String correo;
    private String celular;
    private String username;
    private String cargoNombre;
    private Boolean activo;

    public static TrabajadorResponseDto fromEntity(Trabajador t) {
        return TrabajadorResponseDto.builder()
                .id(t.getId())
                .dni(t.getDni())
                .nombres(t.getNombres())
                .apellidos(t.getApellidos())
                .correo(t.getCorreo())
                .celular(t.getCelular())
                .username(t.getUsername())
                .cargoNombre(t.getCargo() != null ? t.getCargo().getNombre() : null)
                .activo(t.getActivo())
                .build();
    }
}