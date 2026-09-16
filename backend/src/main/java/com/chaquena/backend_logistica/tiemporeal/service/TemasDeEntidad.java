package com.chaquena.backend_logistica.tiemporeal.service;

import com.chaquena.backend_logistica.delivery.domain.OrdenDeliveryInfo;
import com.chaquena.backend_logistica.delivery.domain.Transportista;
import com.chaquena.backend_logistica.delivery.domain.Vehiculo;
import com.chaquena.backend_logistica.inventario.domain.ControlInsumo;
import com.chaquena.backend_logistica.inventario.domain.Insumo;
import com.chaquena.backend_logistica.inventario.domain.LoteInsumo;
import com.chaquena.backend_logistica.mesas.domain.Mesa;
import com.chaquena.backend_logistica.mesas.domain.Reserva;
import com.chaquena.backend_logistica.pagos.domain.Pago;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.pedidos.domain.OrdenDetalle;
import com.chaquena.backend_logistica.pedidos.domain.OrdenDetalleComplemento;
import com.chaquena.backend_logistica.tiemporeal.domain.TemaEnum;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.chaquena.backend_logistica.tiemporeal.domain.TemaEnum.*;

/**
 * Que temas toca el cambio de cada entidad.
 *
 * <p>Se decide por la tabla que se escribio y no por el servicio que la
 * escribio, para que ningun camino se quede sin avisar: la comanda que entra
 * por el bot del mozo, la mesa que se ocupa sola al tomar la comanda o el stock
 * que baja al venderla avisan igual que el boton de la pantalla.
 */
final class TemasDeEntidad {

    private static final Map<Class<?>, Set<TemaEnum>> TEMAS = Map.ofEntries(
            // La comanda aparece en casi todo: la cola de cocina, la mesa que
            // ocupa, el reparto que espera y la caja que la cobra.
            Map.entry(Orden.class, EnumSet.of(COMANDAS, COCINA, REPARTO, MESAS, CAJA)),
            Map.entry(OrdenDetalle.class, EnumSet.of(COMANDAS, COCINA)),
            Map.entry(OrdenDetalleComplemento.class, EnumSet.of(COMANDAS, COCINA)),
            Map.entry(OrdenDeliveryInfo.class, EnumSet.of(COMANDAS, REPARTO)),
            Map.entry(Transportista.class, EnumSet.of(REPARTO)),
            Map.entry(Vehiculo.class, EnumSet.of(REPARTO)),
            Map.entry(Pago.class, EnumSet.of(COMANDAS, CAJA)),
            Map.entry(Mesa.class, EnumSet.of(MESAS)),
            Map.entry(Reserva.class, EnumSet.of(RESERVAS, MESAS)),
            Map.entry(Insumo.class, EnumSet.of(STOCK)),
            Map.entry(LoteInsumo.class, EnumSet.of(STOCK)),
            Map.entry(ControlInsumo.class, EnumSet.of(STOCK)));

    private TemasDeEntidad() {
    }

    /** Vacio si nadie escucha los cambios de esa entidad. */
    static Set<TemaEnum> de(Class<?> entidad) {
        return TEMAS.getOrDefault(entidad, Set.of());
    }
}
