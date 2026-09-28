package com.chaquena.backend_logistica.geo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un punto con su direccion: la respuesta de tocar el mapa y cada sugerencia
 * al escribir.
 *
 * <p>{@code direccion} es lo que va al campo del formulario —calle, numero y
 * distrito, como se escribe en el Peru—; {@code etiqueta} es la linea completa
 * que devuelve el geocodificador, para leerla en la lista de sugerencias.
 * Si no se encontro nada, los dos vienen nulos y las coordenadas son las que se
 * pidieron: el punto sigue sirviendo aunque no tenga nombre.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DireccionDto {

    private String direccion;
    private String etiqueta;
    private Double latitud;
    private Double longitud;
}
