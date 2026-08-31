package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.TipoComplementoEnum;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplementoRequestDto {

    @NotBlank(message = "El nombre del complemento es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String nombre;

    @NotNull(message = "El tipo de complemento es obligatorio")
    private TipoComplementoEnum tipoComplemento;

    @NotNull(message = "El precio adicional es obligatorio")
    @DecimalMin(value = "0.00", message = "El precio adicional no puede ser negativo")
    private BigDecimal precioAdicional;

    /** Insumo que se descuenta al vender el complemento. Opcional. */
    private UUID insumoAsociadoId;

    private Boolean activo;
}
