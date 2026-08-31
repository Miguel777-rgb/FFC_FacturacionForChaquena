package com.chaquena.backend_logistica.shared.mensajeria;

/**
 * Una fila de una lista desplegable. El {@code id} es lo que vuelve como
 * contenido del mensaje cuando la persona la elige, asi que la maquina de
 * estados lo reconoce por su prefijo sin tener que interpretar texto libre.
 *
 * <p>La descripcion es opcional y ambos proveedores la muestran en gris bajo el
 * titulo: se usa para el precio y el stock, que ayudan a elegir pero no deben
 * competir con el nombre.
 */
public record OpcionBot(String id, String titulo, String descripcion) {

    public static OpcionBot de(String id, String titulo) {
        return new OpcionBot(id, titulo, null);
    }
}
