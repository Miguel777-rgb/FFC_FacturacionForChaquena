package com.chaquena.backend_logistica.inventario.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Alta y edicion de un platillo. El {@code PUT} reemplaza: una foto o un
 * tiempo que no llegan se quitan. Sin {@code alergenoIds} los alergenos no se
 * tocan; con una lista vacia se quitan todos.
 */
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

    /** Una imagen ya subida a /api/v1/archivos. */
    private UUID fotoId;

    @Min(value = 1, message = "El tiempo de preparacion es de al menos un minuto")
    @Max(value = 240, message = "El tiempo de preparacion no puede pasar de 240 minutos")
    private Integer tiempoPreparacionMinutos;

    private List<Integer> alergenoIds;
}
