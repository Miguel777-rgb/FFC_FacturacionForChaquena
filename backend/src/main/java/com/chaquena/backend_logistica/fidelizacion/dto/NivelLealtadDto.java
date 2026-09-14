package com.chaquena.backend_logistica.fidelizacion.dto;

import com.chaquena.backend_logistica.fidelizacion.domain.NivelLealtad;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NivelLealtadDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @NotBlank(message = "El nivel necesita un nombre")
    @Size(max = 60, message = "El nombre admite hasta 60 caracteres")
    private String nombre;

    @NotNull(message = "Indica desde cuantos puntos se alcanza el nivel")
    @Min(value = 0, message = "Los puntos minimos no pueden ser negativos")
    private Integer puntosMinimos;

    @NotNull(message = "Indica el descuento del nivel, aunque sea cero")
    @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo")
    @DecimalMax(value = "100.00", message = "El descuento no puede superar 100")
    private BigDecimal porcentajeDescuento;

    public static NivelLealtadDto fromEntity(NivelLealtad n) {
        return NivelLealtadDto.builder()
                .id(n.getId())
                .nombre(n.getNombre())
                .puntosMinimos(n.getPuntosMinimos())
                .porcentajeDescuento(n.getPorcentajeDescuento())
                .build();
    }
}
