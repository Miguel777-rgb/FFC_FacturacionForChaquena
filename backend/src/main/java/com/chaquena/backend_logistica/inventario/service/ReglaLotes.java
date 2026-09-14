package com.chaquena.backend_logistica.inventario.service;

import com.chaquena.backend_logistica.inventario.domain.EstadoVencimientoEnum;
import com.chaquena.backend_logistica.inventario.domain.LoteInsumo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Las cuentas de los lotes: en que orden se consumen, a donde vuelve lo que se
 * repone, cuanto costo lo que salio y como esta cada fecha.
 *
 * <p>Viven aparte del servicio para probarlas sin repositorios. Mutan los lotes
 * que reciben y dejan el guardado a quien las llama, que es quien tiene la
 * fila del insumo bloqueada.
 */
public final class ReglaLotes {

    /** Cuantos dias antes de vencer un lote empieza a pedir atencion. */
    public static final int DIAS_AVISO_VENCIMIENTO = 3;

    /**
     * El dia se cuenta en Lima y no en la zona del servidor: a las nueve de la
     * noche en el local ya es manana en UTC, y un lote que vence hoy apareceria
     * vencido en plena cena.
     */
    public static final ZoneId ZONA_DEL_LOCAL = ZoneId.of("America/Lima");

    /** Primero lo que vence antes; lo que no tiene fecha, al final; a igual fecha, lo que entro primero. */
    private static final Comparator<LoteInsumo> FEFO = Comparator
            .comparing(LoteInsumo::getFechaVencimiento, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(LoteInsumo::getDateCreated, Comparator.nullsLast(Comparator.naturalOrder()));

    /** Cuanto sale de un lote en un movimiento. */
    public record Toma(LoteInsumo lote, BigDecimal cantidad) {
    }

    /** Lo que queda de un insumo, visto por lotes. */
    public record Resumen(LocalDate proximoVencimiento, BigDecimal cantidadVencida,
            BigDecimal cantidadPorVencer, BigDecimal valor, BigDecimal cantidadConCosto) {
    }

    private ReglaLotes() {
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA_DEL_LOCAL);
    }

    public static List<LoteInsumo> ordenFefo(Collection<LoteInsumo> lotes) {
        return lotes.stream().sorted(FEFO).toList();
    }

    /**
     * Saca la cantidad de los lotes en orden FEFO. Si no alcanza, saca lo que
     * hay: la diferencia es stock anterior a los lotes que nadie repartio, y el
     * stock total ya se valido antes de llegar aqui.
     */
    public static List<Toma> consumirFefo(Collection<LoteInsumo> lotes, BigDecimal cantidad) {
        List<Toma> tomas = new ArrayList<>();
        BigDecimal pendiente = cantidad;
        for (LoteInsumo lote : ordenFefo(lotes)) {
            if (pendiente.signum() <= 0) break;
            BigDecimal restante = cero(lote.getCantidadRestante());
            if (restante.signum() <= 0) continue;
            BigDecimal sale = restante.min(pendiente);
            lote.setCantidadRestante(restante.subtract(sale));
            pendiente = pendiente.subtract(sale);
            tomas.add(new Toma(lote, sale));
        }
        return tomas;
    }

    /**
     * Devuelve una cantidad a los lotes de los que ya salio algo, en el orden
     * inverso al FEFO: lo ultimo que se consumio es lo primero que vuelve. Asi
     * lo que se repone al cancelar recupera su fecha en vez de nacer sin ella.
     * Ningun lote pasa de su cantidad inicial.
     *
     * @return lo que no cupo en ningun lote
     */
    public static BigDecimal reponer(Collection<LoteInsumo> lotes, BigDecimal cantidad) {
        List<LoteInsumo> alReves = new ArrayList<>(ordenFefo(lotes));
        Collections.reverse(alReves);
        BigDecimal pendiente = cantidad;
        for (LoteInsumo lote : alReves) {
            if (pendiente.signum() <= 0) break;
            BigDecimal restante = cero(lote.getCantidadRestante());
            BigDecimal hueco = cero(lote.getCantidadInicial()).subtract(restante);
            if (hueco.signum() <= 0) continue;
            BigDecimal vuelve = hueco.min(pendiente);
            lote.setCantidadRestante(restante.add(vuelve));
            pendiente = pendiente.subtract(vuelve);
        }
        return pendiente;
    }

    /**
     * Lo que costo lo que salio. Nulo si algo salio de un lote sin costo o si los
     * lotes no cubrieron todo lo pedido: un costo parcial parece completo y no lo es.
     */
    public static BigDecimal costoDe(List<Toma> tomas, BigDecimal pedido) {
        BigDecimal cubierto = tomas.stream().map(Toma::cantidad).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (tomas.isEmpty() || cubierto.compareTo(pedido) < 0) return null;

        BigDecimal total = BigDecimal.ZERO;
        for (Toma toma : tomas) {
            if (toma.lote().getCostoUnitario() == null) return null;
            total = total.add(toma.cantidad().multiply(toma.lote().getCostoUnitario()));
        }
        return total;
    }

    public static EstadoVencimientoEnum estado(LocalDate vencimiento, LocalDate hoy) {
        if (vencimiento == null) return EstadoVencimientoEnum.SIN_VENCIMIENTO;
        if (vencimiento.isBefore(hoy)) return EstadoVencimientoEnum.VENCIDO;
        if (!vencimiento.isAfter(hoy.plusDays(DIAS_AVISO_VENCIMIENTO))) return EstadoVencimientoEnum.POR_VENCER;
        return EstadoVencimientoEnum.VIGENTE;
    }

    /** Resume lo que queda: el vencimiento mas cercano, lo vencido, lo que vence pronto y su valor. */
    public static Resumen resumir(Collection<LoteInsumo> lotes, LocalDate hoy) {
        LocalDate proximo = null;
        BigDecimal vencida = BigDecimal.ZERO;
        BigDecimal porVencer = BigDecimal.ZERO;
        BigDecimal valor = BigDecimal.ZERO;
        BigDecimal conCosto = BigDecimal.ZERO;

        for (LoteInsumo lote : lotes) {
            BigDecimal restante = cero(lote.getCantidadRestante());
            if (restante.signum() <= 0) continue;

            LocalDate fecha = lote.getFechaVencimiento();
            if (fecha != null && (proximo == null || fecha.isBefore(proximo))) {
                proximo = fecha;
            }
            switch (estado(fecha, hoy)) {
                case VENCIDO -> vencida = vencida.add(restante);
                case POR_VENCER -> porVencer = porVencer.add(restante);
                default -> {
                }
            }
            if (lote.getCostoUnitario() != null) {
                valor = valor.add(restante.multiply(lote.getCostoUnitario()));
                conCosto = conCosto.add(restante);
            }
        }

        return new Resumen(proximo, vencida, porVencer, valor.setScale(2, RoundingMode.HALF_UP), conCosto);
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
