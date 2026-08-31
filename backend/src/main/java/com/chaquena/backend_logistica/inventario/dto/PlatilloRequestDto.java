package com.chaquena.backend_logistica.inventario.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatilloRequestDto {

    @NotNull(message = "La categoria es obligatoria")
    private Integer categoriaId;

    @NotBlank(message = "El nombre del platillo es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    private String nombre;

    private String descripcion;

    @NotNull(message = "El precio de venta es obligatorio")
    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    private BigDecimal precioVentaBase;

    private Boolean activo;
}
