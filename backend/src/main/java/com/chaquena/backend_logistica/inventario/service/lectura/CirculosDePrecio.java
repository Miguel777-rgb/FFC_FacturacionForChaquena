package com.chaquena.backend_logistica.inventario.service.lectura;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Los circulos amarillos con el precio que usa la carta en las bebidas.
 *
 * <p>Tesseract no los separa del resto: lee el numero pegado a la foto de la
 * botella, o no lo lee. Encontrarlos por su color permite leer cada precio
 * aparte y buscar el nombre del producto justo encima.
 *
 * <p>Una mancha cuenta como circulo si es amarilla, casi tan ancha como alta y
 * de un tamano razonable para la pagina: asi se descartan las botellas de Inca
 * Kola, que son del mismo amarillo pero alargadas.
 */
public final class CirculosDePrecio {

    private CirculosDePrecio() {
    }

    public static List<Rectangle> detectar(BufferedImage original) {
        int ancho = original.getWidth();
        int alto = original.getHeight();
        boolean[] amarillo = new boolean[ancho * alto];
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                amarillo[y * ancho + x] = esAmarillo(original.getRGB(x, y));
            }
        }

        // Un circulo mide entre el 3 % y el 7 % del ancho de la pagina.
        int ladoMinimo = Math.max(8, (int) (ancho * 0.03));
        int ladoMaximo = (int) (ancho * 0.075);

        boolean[] visto = new boolean[ancho * alto];
        int[] pila = new int[ancho * alto];
        List<Rectangle> circulos = new ArrayList<>();
        for (int inicio = 0; inicio < amarillo.length; inicio++) {
            if (!amarillo[inicio] || visto[inicio]) {
                continue;
            }
            int arriba = 0;
            pila[arriba++] = inicio;
            visto[inicio] = true;
            int minX = ancho;
            int minY = alto;
            int maxX = 0;
            int maxY = 0;
            int area = 0;
            while (arriba > 0) {
                int p = pila[--arriba];
                int px = p % ancho;
                int py = p / ancho;
                area++;
                minX = Math.min(minX, px);
                maxX = Math.max(maxX, px);
                minY = Math.min(minY, py);
                maxY = Math.max(maxY, py);
                int[] vecinos = {p - 1, p + 1, p - ancho, p + ancho};
                for (int v : vecinos) {
                    if (v < 0 || v >= amarillo.length || visto[v] || !amarillo[v]) {
                        continue;
                    }
                    int vx = v % ancho;
                    if (Math.abs(vx - px) > 1) {
                        continue;
                    }
                    visto[v] = true;
                    pila[arriba++] = v;
                }
            }

            int w = maxX - minX + 1;
            int h = maxY - minY + 1;
            double proporcion = (double) w / h;
            double relleno = (double) area / (w * h);
            if (w >= ladoMinimo && h >= ladoMinimo && w <= ladoMaximo && h <= ladoMaximo
                    && proporcion > 0.8 && proporcion < 1.25
                    && relleno > 0.45 && relleno < 0.9) {
                circulos.add(new Rectangle(minX, minY, w, h));
            }
        }
        circulos.sort((a, b) -> a.y != b.y ? Integer.compare(a.y, b.y) : Integer.compare(a.x, b.x));
        return circulos;
    }

    /** El amarillo de los circulos: rojo y verde altos, azul bajo. */
    static boolean esAmarillo(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return r >= 200 && g >= 160 && b <= 140 && r - b >= 90 && Math.abs(r - g) <= 60;
    }
}
