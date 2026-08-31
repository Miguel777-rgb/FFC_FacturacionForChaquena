package com.chaquena.backend_logistica.reportes.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoTopDto {
    private String platillo;
    private long unidadesVendidas;
    private BigDecimal montoTotal;
}
