package com.chaquena.backend_logistica.inventario.service.lectura;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Lo que se le hace a la foto antes y despues de leerla. Sin Spring, para
 * probarlo con imagenes armadas en la prueba.
 *
 * <p>Tesseract lee mejor letras de unos 30 pixeles de alto. En la carta del
 * local, exportada a 1131 pixeles de ancho, la descripcion mide 12: por eso la
 * pagina se agranda hasta unos 2800 de ancho y se pasa a grises.
 */
public final class ImagenCarta {

    /** Ancho al que se lleva la pagina para leerla. */
    static final int ANCHO_DE_LECTURA = 2800;

    /** Gris por debajo del cual un pixel cuenta como tinta. */
    static final int UMBRAL_TINTA = 140;

    private ImagenCarta() {
    }

    /** La pagina en grises y agrandada, con el factor usado para volver a la original. */
    public record Preparada(BufferedImage gris, double escala) {
    }

    /** Varias zonas apiladas en una sola imagen, y en que franja vertical quedo cada una. */
    public record Apilado(BufferedImage imagen, List<int[]> franjas) {
    }

    public static Preparada preparar(BufferedImage original) {
        double escala = Math.max(1.0, Math.min(3.0, (double) ANCHO_DE_LECTURA / original.getWidth()));
        int ancho = (int) Math.round(original.getWidth() * escala);
        int alto = (int) Math.round(original.getHeight() * escala);
        BufferedImage gris = new BufferedImage(ancho, alto, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gris.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(original, 0, 0, ancho, alto, null);
        g.dispose();
        return new Preparada(gris, escala);
    }

    /**
     * Donde se parte la pagina en columnas: el pasillo sin tinta mas ancho en el
     * tercio central. Sin pasillo, la pagina es una sola columna.
     *
     * @return pares {@code [desde, hasta)} en x
     */
    public static List<int[]> columnas(BufferedImage gris) {
        int ancho = gris.getWidth();
        int alto = gris.getHeight();
        int[] tinta = new int[ancho];
        for (int x = 0; x < ancho; x++) {
            int cuenta = 0;
            for (int y = 0; y < alto; y += 2) {
                if ((gris.getRaster().getSample(x, y, 0)) < UMBRAL_TINTA) {
                    cuenta++;
                }
            }
            tinta[x] = cuenta;
        }

        int limite = Math.max(2, alto / 1000);
        int mejorDesde = -1;
        int mejorLargo = 0;
        int desde = -1;
        for (int x = (int) (ancho * 0.35); x <= (int) (ancho * 0.65); x++) {
            if (tinta[x] <= limite) {
                if (desde < 0) {
                    desde = x;
                }
                int largo = x - desde + 1;
                if (largo > mejorLargo) {
                    mejorLargo = largo;
                    mejorDesde = desde;
                }
            } else {
                desde = -1;
            }
        }

        List<int[]> columnas = new ArrayList<>();
        if (mejorDesde < 0 || mejorLargo < ancho * 0.006) {
            columnas.add(new int[] {0, ancho});
            return columnas;
        }
        int corte = mejorDesde + mejorLargo / 2;
        columnas.add(new int[] {0, corte});
        columnas.add(new int[] {corte, ancho});
        return columnas;
    }

    public static BufferedImage recortar(BufferedImage imagen, Rectangle zona) {
        Rectangle dentro = zona.intersection(new Rectangle(0, 0, imagen.getWidth(), imagen.getHeight()));
        if (dentro.isEmpty()) {
            return new BufferedImage(1, 1, imagen.getType() == 0 ? BufferedImage.TYPE_INT_RGB : imagen.getType());
        }
        BufferedImage copia = new BufferedImage(dentro.width, dentro.height,
                imagen.getType() == 0 ? BufferedImage.TYPE_INT_RGB : imagen.getType());
        Graphics2D g = copia.createGraphics();
        g.drawImage(imagen, -dentro.x, -dentro.y, null);
        g.dispose();
        return copia;
    }

    /**
     * Pega cada zona de precio una debajo de otra, agrandada al doble y en blanco
     * y negro, con aire entre ellas. Asi una sola pasada de Tesseract lee todos
     * los precios de la pagina, y cada numero se reconoce por la franja en que cae.
     */
    public static Apilado apilarParaDigitos(BufferedImage gris, List<Rectangle> zonas) {
        int margen = 30;
        int separacion = 50;
        List<BufferedImage> piezas = new ArrayList<>();
        int anchoMaximo = 1;
        int altoTotal = separacion;
        for (Rectangle zona : zonas) {
            BufferedImage pieza = blancoYNegro(agrandar(recortar(gris, zona), 2));
            piezas.add(pieza);
            anchoMaximo = Math.max(anchoMaximo, pieza.getWidth() + 2 * margen);
            altoTotal += pieza.getHeight() + separacion;
        }

        BufferedImage hoja = new BufferedImage(anchoMaximo, altoTotal, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = hoja.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, anchoMaximo, altoTotal);
        List<int[]> franjas = new ArrayList<>();
        int y = separacion;
        for (BufferedImage pieza : piezas) {
            g.drawImage(pieza, margen, y, null);
            franjas.add(new int[] {y - separacion / 2, y + pieza.getHeight() + separacion / 2});
            y += pieza.getHeight() + separacion;
        }
        g.dispose();
        return new Apilado(hoja, franjas);
    }

    /**
     * El recorte de la foto original que acompana a una fila dudosa, como
     * {@code data:image/jpeg;base64,...} y de como mucho 520 pixeles de ancho.
     */
    public static String recorteComoDataUrl(BufferedImage original, Rectangle zona) {
        BufferedImage recorte = recortar(original, zona);
        if (recorte.getWidth() > 520) {
            double f = 520.0 / recorte.getWidth();
            recorte = agrandar(recorte, f);
        }
        BufferedImage rgb = new BufferedImage(recorte.getWidth(), recorte.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(recorte, 0, 0, Color.WHITE, null);
        g.dispose();

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                MemoryCacheImageOutputStream salida = new MemoryCacheImageOutputStream(bytes)) {
            ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(0.8f);
            escritor.setOutput(salida);
            escritor.write(null, new IIOImage(rgb, null, null), parametros);
            escritor.dispose();
            salida.flush();
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo recortar la foto", e);
        }
    }

    private static BufferedImage agrandar(BufferedImage imagen, double factor) {
        int ancho = Math.max(1, (int) Math.round(imagen.getWidth() * factor));
        int alto = Math.max(1, (int) Math.round(imagen.getHeight() * factor));
        BufferedImage destino = new BufferedImage(ancho, alto,
                imagen.getType() == 0 ? BufferedImage.TYPE_INT_RGB : imagen.getType());
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(imagen, 0, 0, ancho, alto, null);
        g.dispose();
        return destino;
    }

    private static BufferedImage blancoYNegro(BufferedImage gris) {
        BufferedImage bn = new BufferedImage(gris.getWidth(), gris.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < gris.getHeight(); y++) {
            for (int x = 0; x < gris.getWidth(); x++) {
                int valor = gris.getRaster().getSample(x, y, 0) < UMBRAL_TINTA ? 0 : 255;
                bn.getRaster().setSample(x, y, 0, valor);
            }
        }
        return bn;
    }
}
