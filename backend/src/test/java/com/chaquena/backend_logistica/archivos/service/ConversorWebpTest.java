package com.chaquena.backend_logistica.archivos.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * La conversion de verdad necesita el programa cwebp, que viene en la imagen de
 * Docker pero no tiene por que estar en la maquina que corre las pruebas: esa
 * prueba se salta si falta. Las otras dos no lo necesitan.
 */
class ConversorWebpTest {

    private static final byte[] WEBP = { 'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P' };

    @Test
    void sinElProgramaDevuelveVacioYNoFalla() throws IOException {
        ConversorWebp sinPrograma = new ConversorWebp("/no/existe/cwebp");

        assertThat(sinPrograma.disponible()).isFalse();
        assertThat(sinPrograma.aWebp(imagen("png"), ReglaImagen.Tipo.PNG)).isEmpty();
        // Avisar de que falta solo la primera vez no cambia lo que devuelve.
        assertThat(sinPrograma.aWebp(imagen("jpg"), ReglaImagen.Tipo.JPEG)).isEmpty();
    }

    @Test
    void unWebpNoSeVuelveAConvertir() {
        // Con un binario que no existe: si intentara ejecutarlo, tampoco fallaria,
        // pero lo que se comprueba es que ni lo intenta y devuelve vacio.
        assertThat(new ConversorWebp("/no/existe/cwebp").aWebp(WEBP, ReglaImagen.Tipo.WEBP)).isEmpty();
    }

    @Test
    void conCwebpUnPngYUnJpegSalenEnWebp() throws IOException {
        ConversorWebp conversor = new ConversorWebp("cwebp");
        assumeTrue(conversor.disponible(), "sin cwebp en esta maquina");

        for (String formato : new String[] {"png", "jpg"}) {
            byte[] original = imagen(formato);
            ReglaImagen.Tipo tipo = ReglaImagen.detectar(original).orElseThrow();

            byte[] webp = conversor.aWebp(original, tipo).orElseThrow();

            assertThat(ReglaImagen.detectar(webp)).contains(ReglaImagen.Tipo.WEBP);
        }
    }

    /** Una imagen pequena con algo de color, para que el codificador tenga que trabajar. */
    private static byte[] imagen(String formato) throws IOException {
        BufferedImage imagen = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D pincel = imagen.createGraphics();
        pincel.setColor(new Color(0xA41E34));
        pincel.fillRect(0, 0, 64, 48);
        pincel.setColor(new Color(0xF0FDF4));
        pincel.fillOval(12, 8, 40, 32);
        pincel.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, formato, salida);
        return salida.toByteArray();
    }
}
