package com.chaquena.backend_logistica.inventario.dto;

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

/** Un platillo revisado que se crea, o que actualiza al que tiene el mismo nombre. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatilloImportacionDto {

    @NotBlank(message = "Cada platillo necesita un nombre")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    private String nombre;

    private String descripcion;

    @NotNull(message = "Cada platillo necesita un precio")
    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    private BigDecimal precio;

    /**
     * Agrega "Vegetariano." al inicio de la descripcion si no lo dice ya. Objeto y
     * no primitivo: la casilla es opcional en el contrato, y con un primitivo una
     * peticion que no la manda se cae con un 400 en lugar de valer "no".
     */
    private Boolean vegetariano;
}
