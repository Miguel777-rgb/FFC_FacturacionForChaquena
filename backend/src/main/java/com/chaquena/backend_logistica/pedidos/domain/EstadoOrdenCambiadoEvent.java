package com.chaquena.backend_logistica.pedidos.domain;

import java.util.UUID;

/**
 * La comanda paso a otro estado.
 *
 * <p>Lo publica {@code MaquinaEstadosOrden}, que es por donde pasa toda
 * transicion, y se entrega despues de confirmar la transaccion: un cambio que
 * se deshace no avisa a nadie. Asi {@code pedidos} no necesita conocer a quien
 * reacciona, igual que con {@link OrdenCreadaEvent}.
 */
public record EstadoOrdenCambiadoEvent(UUID ordenId, EstadoOrdenEnum estado) {
}
