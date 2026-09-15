package com.chaquena.backend_logistica.mesas.service;

import java.util.List;
import java.util.Locale;

/**
 * El plano de cada zona es una rejilla de {@link #COLUMNAS} columnas. Una mesa
 * ocupa un rectangulo de celdas, y dos mesas de la misma zona no pueden ocupar
 * la misma celda. Sin Spring ni base de datos.
 */
public final class ReglaPlano {

    /** Doce columnas se dividen bien en dos, tres, cuatro y seis mesas por fila. */
    public static final int COLUMNAS = 12;

    public static final int FILAS = 40;

    public static final int LADO_MAXIMO = 4;

    public record Posicion(String numero, String zona, int columna, int fila, int ancho, int alto) {

        boolean pisa(Posicion otra) {
            return columna < otra.columna + otra.ancho && otra.columna < columna + ancho
                    && fila < otra.fila + otra.alto && otra.fila < fila + alto;
        }
    }

    public record Hueco(int columna, int fila) {
    }

    private ReglaPlano() {
    }

    /** La zona con que se comparan las mesas: "Terraza" y " terraza" son la misma. */
    public static String claveDeZona(String zona) {
        return zona == null ? "" : zona.trim().toLowerCase(Locale.ROOT);
    }

    public static void validar(List<Posicion> mesas) {
        for (Posicion p : mesas) {
            if (p.ancho() < 1 || p.alto() < 1 || p.ancho() > LADO_MAXIMO || p.alto() > LADO_MAXIMO) {
                throw new IllegalArgumentException("La mesa " + p.numero() + " debe medir entre 1 y "
                        + LADO_MAXIMO + " celdas por lado.");
            }
            if (p.columna() < 0 || p.fila() < 0 || p.columna() + p.ancho() > COLUMNAS
                    || p.fila() + p.alto() > FILAS) {
                throw new IllegalArgumentException("La mesa " + p.numero() + " se sale del plano.");
            }
        }
        for (int i = 0; i < mesas.size(); i++) {
            for (int j = i + 1; j < mesas.size(); j++) {
                Posicion a = mesas.get(i);
                Posicion b = mesas.get(j);
                if (claveDeZona(a.zona()).equals(claveDeZona(b.zona())) && a.pisa(b)) {
                    throw new IllegalArgumentException("Las mesas " + a.numero() + " y " + b.numero()
                            + " se pisan en el plano.");
                }
            }
        }
    }

    /**
     * El primer sitio libre, de arriba abajo y de izquierda a derecha, dejando
     * una celda de pasillo alrededor. Si no queda sitio con pasillo, se acepta
     * pegada a otra: es mejor que no poder dar de alta la mesa.
     */
    public static Hueco primerHueco(List<Posicion> ocupadas, int ancho, int alto) {
        for (int margen = 1; margen >= 0; margen--) {
            for (int fila = 0; fila + alto <= FILAS; fila++) {
                for (int columna = 0; columna + ancho <= COLUMNAS; columna++) {
                    Posicion conPasillo = new Posicion("", "", columna - margen, fila - margen,
                            ancho + 2 * margen, alto + 2 * margen);
                    if (ocupadas.stream().noneMatch(conPasillo::pisa)) {
                        return new Hueco(columna, fila);
                    }
                }
            }
        }
        throw new IllegalArgumentException("No queda sitio en el plano de esa zona.");
    }
}
