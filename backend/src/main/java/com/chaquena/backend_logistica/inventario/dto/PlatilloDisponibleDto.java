package com.chaquena.backend_logistica.inventario.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Platillo de la carta con la disponibilidad ya resuelta contra el stock.
 * El POS y los bots consumen esto para no ofrecer lo que no se
 * puede preparar.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatilloDisponibleDto {

    private UUID id;
    private String nombre;
    private String descripcion;
    private Integer categoriaId;
    private String categoriaNombre;
    private BigDecimal precioVentaBase;

    /** Cuantas porciones alcanzan los insumos actuales. null = receta sin insumos. */
    private Integer porcionesPosibles;

    private boolean disponible;

    /** Insumos que impiden prepararlo, para que cocina sepa que reponer. */
    private List<String> insumosFaltantes;
}
