package com.chaquena.backend_logistica.auth.dto;

import com.chaquena.backend_logistica.auth.domain.Cargo;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CargoResponseDto {

    private Integer id;
    private String nombre;
    private String descripcion;
    private List<String> rolesNombres;

    public static CargoResponseDto fromEntity(Cargo cargo) {
        List<String> roles = (cargo.getCargoRoles() != null) ? cargo.getCargoRoles().stream()
                .map(cr -> cr.getRol().getNombre())
                .toList() : List.of();

        return CargoResponseDto.builder()
                .id(cargo.getId())
                .nombre(cargo.getNombre())
                .descripcion(cargo.getDescripcion())
                .rolesNombres(roles)
                .build();
    }
}