package com.chaquena.backend_logistica.tiemporeal.domain;

import java.util.Collection;
import java.util.Set;

/**
 * De que trata un aviso en tiempo real, y quien puede recibirlo.
 *
 * <p>El aviso no lleva datos: solo dice "cambio algo de esto" y cada pantalla
 * vuelve a pedir lo suyo por el endpoint de siempre, que es el que aplica los
 * permisos finos. Aun asi se filtra por cargo en el servidor, para que cocina
 * no se entere del ritmo de la caja ni el almacen del de las mesas.
 *
 * <p>Los cargos son los mismos de los {@code @PreAuthorize} de las pantallas
 * que escuchan cada tema. ADMIN los recibe todos.
 */
public enum TemaEnum {

    /** Ordenes, tablero y cajon de detalle. */
    COMANDAS("MOZO", "CAJA"),
    /** La cola de cocina. */
    COCINA("COCINA"),
    /** El tablero de reparto: lo miran el conductor y el mozo que despacha. */
    REPARTO("DELIVERY", "MOZO"),
    /** Estado de las mesas y su comanda activa. */
    MESAS("MOZO", "CAJA"),
    /** La agenda de reservas. */
    RESERVAS("MOZO", "CAJA"),
    /** Pagos por acreditar y alertas de fraude. */
    CAJA("CAJA"),
    /** Stock, lotes y vencimientos. CAJA lo recibe por los insumos bajo minimo del tablero. */
    STOCK("ALMACEN", "CAJA");

    private static final String ADMIN = "ADMIN";

    private final Set<String> cargos;

    TemaEnum(String... cargos) {
        this.cargos = Set.of(cargos);
    }

    /** Si una sesion con estos cargos puede recibir avisos de este tema. */
    public boolean loRecibe(Collection<String> cargosDeLaSesion) {
        return cargosDeLaSesion.contains(ADMIN) || cargosDeLaSesion.stream().anyMatch(cargos::contains);
    }
}
