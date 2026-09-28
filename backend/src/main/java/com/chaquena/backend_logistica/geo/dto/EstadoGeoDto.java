package com.chaquena.backend_logistica.geo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Si hay geocodificador configurado. Sin clave el mapa sigue sirviendo para
 * marcar el punto, pero la direccion se escribe a mano; la pantalla lo dice
 * antes de que alguien toque el mapa esperando que se complete sola.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadoGeoDto {

    private Boolean geocodificacion;
}
