package com.chaquena.backend_logistica.shared.websocket;

/** Cambio el numero de mozos distintos con el WebSocket abierto. */
public record PresenciaCambiadaEvent(int mozosConectados) {
}
