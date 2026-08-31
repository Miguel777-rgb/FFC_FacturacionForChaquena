package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.Platillo;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatilloResponseDto {

    private UUID id;
    private Integer categoriaId;
    private String categoriaNombre;
    private String nombre;
    private String descripcion;
    private BigDecimal precioVentaBase;
    private Boolean activo;
    private List<RecetaItemDto> receta;

    public static PlatilloResponseDto fromEntity(Platillo platillo) {
        return construir(platillo, false);
    }

    public static PlatilloResponseDto conReceta(Platillo platillo) {
        return construir(platillo, true);
    }

    private static PlatilloResponseDto construir(Platillo platillo, boolean incluirReceta) {
        return PlatilloResponseDto.builder()
                .id(platillo.getId())
                .categoriaId(platillo.getCategoria() != null ? platillo.getCategoria().getId() : null)
                .categoriaNombre(platillo.getCategoria() != null ? platillo.getCategoria().getNombre() : null)
                .nombre(platillo.getNombre())
                .descripcion(platillo.getDescripcion())
                .precioVentaBase(platillo.getPrecioVentaBase())
                .activo(platillo.getActivo())
                .receta(incluirReceta && platillo.getReceta() != null
                        ? platillo.getReceta().stream().map(RecetaItemDto::fromEntity).toList()
                        : null)
                .build();
    }
}
