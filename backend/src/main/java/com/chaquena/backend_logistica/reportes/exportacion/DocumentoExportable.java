package com.chaquena.backend_logistica.reportes.exportacion;

import java.util.List;

/**
 * Un reporte listo para escribirse, sin saber todavia en que formato.
 *
 * <p>Cada reporte arma sus tablas una sola vez y los dos escritores las pasan a
 * pdf o a xlsx. Asi el pdf y la hoja de calculo de un mismo rango nunca dicen
 * cosas distintas.
 *
 * @param titulo  lo que se lee primero, como "Ventas"
 * @param bajada  el rango o el momento, ya traducido
 * @param tablas  una hoja del xlsx por tabla; en el pdf van una debajo de otra
 */
public record DocumentoExportable(String titulo, String bajada, List<Tabla> tablas) {

    /**
     * @param filas cada valor es un {@code String}, un {@code Number}, un
     *              {@code LocalDate}, un {@code ZonedDateTime} o {@code null}
     * @param pie   la fila de totales, o {@code null} si no la hay
     */
    public record Tabla(String titulo, List<Columna> columnas, List<List<Object>> filas, List<Object> pie) {

        public Tabla {
            if (pie != null && pie.size() != columnas.size()) {
                throw new IllegalArgumentException("El pie de '" + titulo + "' no tiene una celda por columna");
            }
        }
    }

    public record Columna(String nombre, TipoCelda tipo) {
    }

    /** Como se muestra una celda: decide la alineacion, el formato y el ancho. */
    public enum TipoCelda {
        TEXTO,
        ENTERO,
        /** Cantidades de stock: hasta tres decimales, porque hay kilos y litros. */
        CANTIDAD,
        SOLES,
        FECHA,
        FECHA_HORA;

        boolean esNumero() {
            return this == ENTERO || this == CANTIDAD || this == SOLES;
        }
    }
}
