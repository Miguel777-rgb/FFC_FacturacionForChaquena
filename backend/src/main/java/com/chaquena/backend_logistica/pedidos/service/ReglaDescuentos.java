package com.chaquena.backend_logistica.pedidos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Las cuentas de una comanda que no dependen de nada mas que de sus numeros.
 *
 * <p>Viven aparte del servicio para poder probarlas sin levantar repositorios:
 * un centimo mal redondeado en el IGV es justo el tipo de error que no se ve
 * en pantalla y si en el cierre de caja.
 */
public final class ReglaDescuentos {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private ReglaDescuentos() {
    }

    /** Lo que rebaja un porcentaje y un monto fijo sobre un subtotal. Cualquiera de los dos puede faltar. */
    public static BigDecimal descuentoDe(BigDecimal subtotal, BigDecimal porcentaje, BigDecimal monto) {
        BigDecimal total = BigDecimal.ZERO;
        if (porcentaje != null && porcentaje.signum() > 0) {
            total = total.add(subtotal.multiply(porcentaje).divide(CIEN, 2, RoundingMode.HALF_UP));
        }
        if (monto != null && monto.signum() > 0) {
            total = total.add(monto);
        }
        return total;
    }

    /**
     * Si el descuento del nivel desplaza al del cupon. No se acumulan: gana el
     * que mas rebaja. En empate gana el nivel, porque el cliente se queda con
     * el cupon para otra visita en vez de gastarlo en algo que ya tenia.
     */
    public static boolean ganaElNivel(BigDecimal descuentoCupon, BigDecimal descuentoNivel) {
        if (descuentoNivel == null || descuentoNivel.signum() <= 0) return false;
        BigDecimal cupon = descuentoCupon != null ? descuentoCupon : BigDecimal.ZERO;
        return descuentoNivel.compareTo(cupon) >= 0;
    }

    /**
     * La base imponible de un total que ya incluye el IGV. Los precios de la
     * carta lo llevan dentro, asi que el impuesto se desglosa, no se suma.
     */
    public static BigDecimal baseImponible(BigDecimal total, BigDecimal porcentajeIgv) {
        if (total == null) return null;
        if (porcentajeIgv == null || porcentajeIgv.signum() <= 0) {
            return total.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal divisor = BigDecimal.ONE.add(porcentajeIgv.divide(CIEN, 6, RoundingMode.HALF_UP));
        return total.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    /** El IGV contenido en un total. Base mas IGV da siempre el total exacto, al centimo. */
    public static BigDecimal igv(BigDecimal total, BigDecimal porcentajeIgv) {
        if (total == null) return null;
        return total.setScale(2, RoundingMode.HALF_UP).subtract(baseImponible(total, porcentajeIgv));
    }
}
