package com.chaquena.backend_logistica.pedidos.service;

import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenCambiadoEvent;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Unica autoridad sobre las transiciones: KDS, despacho y caja pasan todos por aqui. */
@Component
@RequiredArgsConstructor
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

    private final ApplicationEventPublisher eventos;

    /** Estatica a proposito: los DTO la publican y el frontend pinta sus botones sin copiar la tabla. */
    public static Set<EstadoOrdenEnum> transicionesDesde(EstadoOrdenEnum actual) {
        return actual == null ? Set.of() : TRANSICIONES.getOrDefault(actual, Set.of());
    }

    /** Si la comanda todavia admite cambios en sus lineas. */
    public static boolean esEditable(EstadoOrdenEnum actual) {
        return actual != null && EDITABLES.contains(actual);
    }

    public boolean esTransicionValida(EstadoOrdenEnum actual, EstadoOrdenEnum destino) {
        if (actual == null || destino == null || actual == destino) {
            return false;
        }
        return transicionesDesde(actual).contains(destino);
    }

    public void validar(EstadoOrdenEnum actual, EstadoOrdenEnum destino) {
        if (actual == destino) {
            throw new ConflictoException("La comanda ya esta en estado " + destino + ".");
        }
        if (!esTransicionValida(actual, destino)) {
            throw new ConflictoException(
                    "No se puede pasar de " + actual + " a " + destino + ". "
                            + "Transiciones permitidas desde " + actual + ": "
                            + transicionesDesde(actual) + ".");
        }
    }

    /** Aplica la transicion y sella los cronometros de la etapa; persistir es cosa del servicio. */
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
        // Quien escucha lo hace con AFTER_COMMIT: si el llamador deshace su
        // transaccion, el cambio nunca se anuncia.
        eventos.publishEvent(new EstadoOrdenCambiadoEvent(orden.getId(), destino));
    }

    /** Mesa y retiro se entregan en el mostrador: solo el delivery pasa por despacho. */
    public boolean requiereDespacho(TipoOrdenEnum tipoOrden) {
        return tipoOrden == TipoOrdenEnum.DELIVERY;
    }
}
