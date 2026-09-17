package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.TipoComplementoEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Un adicional revisado. Si ya hay uno con el mismo nombre, se actualiza su precio. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplementoImportacionDto {

    @NotBlank(message = "Cada complemento necesita un nombre")
    @Size(max = 100, message = "El nombre del complemento admite hasta 100 caracteres")
    private String nombre;

    @NotNull(message = "Cada complemento necesita un tipo")
    private TipoComplementoEnum tipo;

    @NotNull(message = "Cada complemento necesita un precio")
    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    private BigDecimal precio;
}
