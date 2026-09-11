package com.chaquena.backend_logistica.auth.dto;

import com.chaquena.backend_logistica.auth.domain.Rol;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolResponseDto {

    private Integer id;
    private String nombre;
    private String descripcion;

    public static RolResponseDto fromEntity(Rol rol) {
        return RolResponseDto.builder()
                .id(rol.getId())
                .nombre(rol.getNombre())
                .descripcion(rol.getDescripcion())
                .build();
    }
}
