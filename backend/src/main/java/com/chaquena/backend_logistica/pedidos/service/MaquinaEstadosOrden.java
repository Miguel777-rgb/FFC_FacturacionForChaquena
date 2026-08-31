package com.chaquena.backend_logistica.pedidos.service;

import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Unico lugar que decide si una transicion de estado es legal. El KDS, el
 * despacho y la caja pasan todos por aqui, que es lo que impide que una orden
 * ya entregada vuelva a cocina o que una cancelada se cobre.
 */
@Component
public class MaquinaEstadosOrden {

    private static final Map<EstadoOrdenEnum, Set<EstadoOrdenEnum>> TRANSICIONES = Map.of(
            EstadoOrdenEnum.ENCOLADO, EnumSet.of(
                    EstadoOrdenEnum.EN_PREPARACION, EstadoOrdenEnum.CANCELADO, EstadoOrdenEnum.FRAUDULENTO),
            EstadoOrdenEnum.EN_PREPARACION, EnumSet.of(
                    EstadoOrdenEnum.EN_DESPACHO, EstadoOrdenEnum.ENTREGADO,
                    EstadoOrdenEnum.CANCELADO, EstadoOrdenEnum.FRAUDULENTO),
            EstadoOrdenEnum.EN_DESPACHO, EnumSet.of(
                    EstadoOrdenEnum.ENTREGADO, EstadoOrdenEnum.CANCELADO, EstadoOrdenEnum.FRAUDULENTO),
            EstadoOrdenEnum.ENTREGADO, EnumSet.of(
                    EstadoOrdenEnum.PAGADO, EstadoOrdenEnum.FRAUDULENTO),
            EstadoOrdenEnum.PAGADO, EnumSet.of(
                    EstadoOrdenEnum.CONCLUIDO, EstadoOrdenEnum.FRAUDULENTO),
            EstadoOrdenEnum.CONCLUIDO, EnumSet.noneOf(EstadoOrdenEnum.class),
            EstadoOrdenEnum.CANCELADO, EnumSet.noneOf(EstadoOrdenEnum.class),
            EstadoOrdenEnum.FRAUDULENTO, EnumSet.noneOf(EstadoOrdenEnum.class));

    /** Estados en los que la comanda todavia se puede editar. */
    public static final Set<EstadoOrdenEnum> EDITABLES = EnumSet.of(EstadoOrdenEnum.ENCOLADO);

    /** Estados que cuentan como venta cerrada para reportes y arqueo. */
    public static final Set<EstadoOrdenEnum> VENTA_EFECTIVA = EnumSet.of(
            EstadoOrdenEnum.ENTREGADO, EstadoOrdenEnum.PAGADO, EstadoOrdenEnum.CONCLUIDO);

    public boolean esTransicionValida(EstadoOrdenEnum actual, EstadoOrdenEnum destino) {
        if (actual == null || destino == null || actual == destino) {
            return false;
        }
        return TRANSICIONES.getOrDefault(actual, Set.of()).contains(destino);
    }

    public void validar(EstadoOrdenEnum actual, EstadoOrdenEnum destino) {
        if (actual == destino) {
            throw new ConflictoException("La comanda ya esta en estado " + destino + ".");
        }
        if (!esTransicionValida(actual, destino)) {
            throw new ConflictoException(
                    "No se puede pasar de " + actual + " a " + destino + ". "
                            + "Transiciones permitidas desde " + actual + ": "
                            + TRANSICIONES.getOrDefault(actual, Set.of()) + ".");
        }
    }

    /**
     * Aplica la transicion y sella las marcas de tiempo que correspondan a la
     * etapa. No persiste: eso queda del lado del servicio que llama.
     */
    public void aplicar(Orden orden, EstadoOrdenEnum destino) {
        validar(orden.getEstado(), destino);

        ZonedDateTime ahora = ZonedDateTime.now();
        switch (destino) {
            case EN_PREPARACION -> {
                orden.setTiempoInicioCocina(ahora);
                if (Boolean.FALSE.equals(orden.getFlagCierreRecepcion())) {
                    orden.setFlagCierreRecepcion(true);
                    orden.setTiempoCierreRecepcion(ahora);
                }
            }
            case EN_DESPACHO -> {
                if (Boolean.FALSE.equals(orden.getFlagCierrePlatillo())) {
                    orden.setFlagCierrePlatillo(true);
                    orden.setTiempoCierrePlatillo(ahora);
                }
            }
            case ENTREGADO -> {
                orden.setFlagCierreDespacho(true);
                orden.setTiempoCierreDespacho(ahora);
                if (Boolean.FALSE.equals(orden.getFlagCierrePlatillo())) {
                    orden.setFlagCierrePlatillo(true);
                    orden.setTiempoCierrePlatillo(ahora);
                }
            }
            case CONCLUIDO, CANCELADO, FRAUDULENTO -> orden.setTiempoFinGlobal(ahora);
            default -> {
                // PAGADO no sella cronometro: el ciclo cierra en CONCLUIDO.
            }
        }

        orden.setEstado(destino);
        orden.setModifiedBy(UsuarioActual.username());
    }

    /**
     * Una comanda de mesa o de retiro no pasa por despacho: se entrega en el
     * mostrador. Delivery si exige el paso intermedio.
     */
    public boolean requiereDespacho(TipoOrdenEnum tipoOrden) {
        return tipoOrden == TipoOrdenEnum.DELIVERY;
    }
}
