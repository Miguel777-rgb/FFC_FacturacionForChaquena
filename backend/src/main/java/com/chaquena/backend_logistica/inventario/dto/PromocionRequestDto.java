package com.chaquena.backend_logistica.inventario.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromocionRequestDto {

    @NotBlank(message = "El nombre de la promocion es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String nombre;

    private String descripcion;

    @DecimalMin(value = "0.00", message = "El porcentaje de descuento no puede ser negativo")
    @DecimalMax(value = "100.00", message = "El porcentaje de descuento no puede superar 100")
    private BigDecimal porcentajeDescuento;

    @DecimalMin(value = "0.00", message = "El monto de descuento no puede ser negativo")
    private BigDecimal montoDescuento;

    private Boolean requiereInsumoExtra;

    /** Insumo del regalo (gaseosa, postre). Obligatorio si requiereInsumoExtra. */
    private UUID insumoExtraId;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private ZonedDateTime fechaInicio;

    @NotNull(message = "La fecha de fin es obligatoria")
    private ZonedDateTime fechaFin;

    private Boolean activa;
}
