package com.chaquena.backend_logistica.fidelizacion.dto;

import com.chaquena.backend_logistica.fidelizacion.domain.ConfiguracionLocal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionLocalDto {

    @Min(value = 1, message = "El umbral de calificaciones debe ser al menos 1")
    private Integer calificacionesParaCupon;

    @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo")
    @DecimalMax(value = "100.00", message = "El descuento no puede superar 100")
    private BigDecimal porcentajeDescuentoCupon;

    @Min(value = 1, message = "La vigencia debe ser de al menos un dia")
    private Integer diasVigenciaCupon;

    @Min(value = 1, message = "El objetivo de cocina debe ser de al menos un minuto")
    private Integer minutosObjetivoCocina;

    public static ConfiguracionLocalDto fromEntity(ConfiguracionLocal c) {
        return ConfiguracionLocalDto.builder()
                .calificacionesParaCupon(c.getCalificacionesParaCupon())
                .porcentajeDescuentoCupon(c.getPorcentajeDescuentoCupon())
                .diasVigenciaCupon(c.getDiasVigenciaCupon())
                .minutosObjetivoCocina(c.getMinutosObjetivoCocina())
                .build();
    }

    public ConfiguracionLocal toEntity() {
        return ConfiguracionLocal.builder()
                .calificacionesParaCupon(calificacionesParaCupon)
                .porcentajeDescuentoCupon(porcentajeDescuentoCupon)
                .diasVigenciaCupon(diasVigenciaCupon)
                .minutosObjetivoCocina(minutosObjetivoCocina)
                .build();
    }
}
