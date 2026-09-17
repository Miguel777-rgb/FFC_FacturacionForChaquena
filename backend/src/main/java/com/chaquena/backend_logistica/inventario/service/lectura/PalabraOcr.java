package com.chaquena.backend_logistica.inventario.service.lectura;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Una palabra que Tesseract reconocio, con su caja en pixeles.
 *
 * <p>La posicion importa tanto como el texto: en una carta a dos columnas el
 * precio es la palabra que esta a la derecha, a la altura del nombre, y la
 * descripcion es la linea de letra mas chica que viene debajo.
 *
 * @param confianza de 0 a 100, la que da Tesseract
 * @param bloque    junto con {@code parrafo} y {@code linea}, la linea a la que pertenece
 */
public record PalabraOcr(String texto, int izquierda, int arriba, int ancho, int alto, double confianza,
        int bloque, int parrafo, int linea) {

    public int derecha() {
        return izquierda + ancho;
    }

    public int abajo() {
        return arriba + alto;
    }

    public double centroX() {
        return izquierda + ancho / 2.0;
    }

    public double centroY() {
        return arriba + alto / 2.0;
    }

    public Rectangle caja() {
        return new Rectangle(izquierda, arriba, ancho, alto);
    }

    public PalabraOcr desplazada(int dx, int dy) {
        return new PalabraOcr(texto, izquierda + dx, arriba + dy, ancho, alto, confianza, bloque, parrafo, linea);
    }

    /**
     * Las palabras de una salida TSV de Tesseract ({@code -c tessedit_create_tsv=1}).
     * Solo el nivel 5, que es el de palabra, y solo las que traen texto.
     */
    public static List<PalabraOcr> deTsv(String tsv) {
        List<PalabraOcr> palabras = new ArrayList<>();
        if (tsv == null) {
            return palabras;
        }
        for (String fila : tsv.split("\\R")) {
            String[] c = fila.split("\t", -1);
            if (c.length < 12 || !"5".equals(c[0])) {
                continue;
            }
            String texto = c[11].trim();
            if (texto.isEmpty()) {
                continue;
            }
            try {
                palabras.add(new PalabraOcr(texto,
                        Integer.parseInt(c[6]), Integer.parseInt(c[7]),
                        Integer.parseInt(c[8]), Integer.parseInt(c[9]),
                        Double.parseDouble(c[10]),
                        Integer.parseInt(c[2]), Integer.parseInt(c[3]), Integer.parseInt(c[4])));
            } catch (NumberFormatException e) {
                // Una fila rota no invalida la pagina: se salta.
            }
        }
        return palabras;
    }
}
