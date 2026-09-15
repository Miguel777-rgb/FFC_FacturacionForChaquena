package com.chaquena.backend_logistica.inventario.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cuanto cuesta hacer un platillo y cuanto deja. Sin Spring ni base de datos.
 *
 * <p>El costo no se guarda: se calcula cada vez con la receta y el ultimo
 * costo con que entro cada insumo. Guardarlo obligaria a recalcularlo con cada
 * compra, y un costo viejo en la carta es peor que ninguno.
 *
 * <p>Si un insumo de la receta nunca entro con costo, no hay costo. Sumar solo
 * los que si lo tienen daria un plato mas barato de lo que es y un margen que
 * no existe; se devuelve en cambio que insumos faltan, para que se sepa que
 * compra registrar.
 */
public final class ReglaCosto {

    public record Linea(UUID insumoId, String insumo, BigDecimal cantidad) {
    }

    public record Costo(BigDecimal costo, BigDecimal margen, BigDecimal margenPorcentaje,
            List<String> insumosSinCosto) {

        /** Sin receta no se sabe que lleva, y un costo cero seria mentir. */
        public static final Costo SIN_RECETA = new Costo(null, null, null, List.of());
    }

    private static final BigDecimal CIEN = new BigDecimal("100");

    private ReglaCosto() {
    }

    public static Costo calcular(BigDecimal precio, List<Linea> receta, Map<UUID, BigDecimal> costoUnitario) {
        if (receta == null || receta.isEmpty()) {
            return Costo.SIN_RECETA;
        }

        List<String> sinCosto = receta.stream()
                .filter(l -> !costoUnitario.containsKey(l.insumoId()))
                .map(Linea::insumo)
                .distinct()
                .sorted()
                .toList();
        if (!sinCosto.isEmpty()) {
            return new Costo(null, null, null, sinCosto);
        }

        BigDecimal costo = receta.stream()
                .map(l -> l.cantidad().multiply(costoUnitario.get(l.insumoId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (precio == null) {
            return new Costo(costo, null, null, List.of());
        }
        BigDecimal margen = precio.subtract(costo).setScale(2, RoundingMode.HALF_UP);
        BigDecimal porcentaje = precio.signum() > 0
                ? margen.multiply(CIEN).divide(precio, 1, RoundingMode.HALF_UP)
                : null;
        return new Costo(costo, margen, porcentaje, List.of());
    }
}
