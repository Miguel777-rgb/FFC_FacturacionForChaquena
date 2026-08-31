package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.CategoriaPlatillo;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoriaResponseDto {

    private Integer id;
    private String nombre;
    private String descripcion;

    public static CategoriaResponseDto fromEntity(CategoriaPlatillo categoria) {
        return CategoriaResponseDto.builder()
                .id(categoria.getId())
                .nombre(categoria.getNombre())
                .descripcion(categoria.getDescripcion())
                .build();
    }
}
