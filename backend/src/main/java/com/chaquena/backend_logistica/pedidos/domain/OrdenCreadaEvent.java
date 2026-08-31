package com.chaquena.backend_logistica.pedidos.domain;

import java.util.UUID;

/**
 * La comanda entro al sistema y cocina tiene que enterarse.
 *
 * <p>Se publica dentro de la transaccion de creacion pero se entrega despues de
 * confirmarla ({@code AFTER_COMMIT}). Ese detalle es el que hace segura la
 * notificacion: si el descuento de stock o el calculo de promociones falla, la
 * transaccion se deshace y el aviso nunca sale, en lugar de mandar a cocina una
 * comanda que no llego a existir. Y al reves, un fallo del bot al publicar no
 * puede tumbar la venta, porque cuando se ejecuta ya esta escrita.
 *
 * <p>Es tambien lo que evita que {@code pedidos} tenga que conocer a los bots:
 * publica un hecho y quien quiera reaccionar se suscribe.
 */
public record OrdenCreadaEvent(UUID ordenId) {
}
