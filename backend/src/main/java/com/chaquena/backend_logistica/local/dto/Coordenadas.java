package com.chaquena.backend_logistica.local.dto;

/** Un punto del mapa, en grados decimales (WGS84, lo que usan el GPS y los mapas web). */
public record Coordenadas(double latitud, double longitud) {
}
