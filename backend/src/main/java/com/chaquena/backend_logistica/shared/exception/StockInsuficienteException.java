package com.chaquena.backend_logistica.shared.exception;

import lombok.Getter;

import java.util.List;

/**
 * No hay insumos suficientes para atender la comanda. Se traduce a HTTP 422
 * y devuelve el detalle de que falto, para que el mozo pueda decidir entre
 * cambiar, esperar, suplir o cancelar.
 */
@Getter
public class StockInsuficienteException extends RuntimeException {

    private final List<String> faltantes;

    public StockInsuficienteException(String mensaje, List<String> faltantes) {
        super(mensaje);
        this.faltantes = faltantes != null ? faltantes : List.of();
    }
}
