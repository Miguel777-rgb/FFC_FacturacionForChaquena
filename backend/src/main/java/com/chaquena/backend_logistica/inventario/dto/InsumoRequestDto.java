package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.TipoInsumoEnum;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsumoRequestDto {

    @NotBlank(message = "El nombre del insumo es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String nombre;

    @NotNull(message = "El tipo de insumo es obligatorio")
    private TipoInsumoEnum tipoInsumo;

    @NotBlank(message = "La unidad de medida es obligatoria")
    @Size(max = 20, message = "La unidad de medida no puede exceder 20 caracteres")
    private String unidadMedida;

    @DecimalMin(value = "0.000", message = "El stock minimo no puede ser negativo")
    private BigDecimal stockMinimo;
}
