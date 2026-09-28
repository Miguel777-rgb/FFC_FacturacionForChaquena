package com.chaquena.backend_logistica.geo.service;

import com.chaquena.backend_logistica.geo.dto.DireccionDto;
import com.chaquena.backend_logistica.geo.dto.EstadoGeoDto;
import com.chaquena.backend_logistica.geo.dto.RutaDto;

import java.util.List;

/**
 * Direcciones y distancias sobre el mapa.
 *
 * <p>Ninguno de estos metodos falla porque el servicio externo falle: sin clave,
 * sin red o con la cuota gastada devuelven vacio, y la pantalla deja escribir
 * la direccion a mano. Un delivery nunca queda bloqueado por el geocodificador.
 */
public interface GeoService {

    EstadoGeoDto estado();

    /** La direccion del punto tocado en el mapa. Sin resultado, direccion y etiqueta nulas. */
    DireccionDto direccionEn(double latitud, double longitud);

    /** Direcciones que empiezan como lo escrito, las mas cercanas al local primero. */
    List<DireccionDto> sugerencias(String texto);

    /** Distancia y tiempo en auto desde el local. Nulos si el local no esta marcado. */
    RutaDto rutaDesdeElLocal(double latitud, double longitud);
}
