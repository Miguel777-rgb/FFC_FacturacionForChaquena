package com.chaquena.backend_logistica.reportes.exportacion;

import com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable.Columna;
import com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable.Tabla;
import com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable.TipoCelda;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Escribe un {@link DocumentoExportable} como pdf para imprimir o archivar.
 *
 * <p>Usa la Helvetica que todo lector de pdf ya trae, asi el archivo no embebe
 * fuentes y el backend no depende de las del sistema. Cubre las tildes, la
 * enie y la cedilla, que es lo que piden los tres idiomas.
 *
 * <p>Con mas de cinco columnas la pagina va apaisada. La cabecera de cada tabla
 * se repite al pasar de pagina.
 */
public final class EscritorPdf {

    private static final Color BORGONA = new Color(0xA4, 0x1E, 0x34);
    private static final Color TINTA = new Color(0x1F, 0x29, 0x37);
    private static final Color TENUE = new Color(0x6B, 0x72, 0x80);
    private static final Color LINEA = new Color(0xE5, 0xE7, 0xEB);
    private static final Color CEBRA = new Color(0xF9, 0xF7, 0xF7);

    private static final Font TITULO = new Font(Font.HELVETICA, 18, Font.BOLD, BORGONA);
    private static final Font BAJADA = new Font(Font.HELVETICA, 9, Font.NORMAL, TENUE);
    private static final Font SUBTITULO = new Font(Font.HELVETICA, 12, Font.BOLD, TINTA);
    private static final Font CABECERA = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);
    private static final Font CUERPO = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TINTA);
    private static final Font PIE = new Font(Font.HELVETICA, 8.5f, Font.BOLD, TINTA);

    private static final int COLUMNAS_PARA_APAISAR = 5;

    private EscritorPdf() {
    }

    public static byte[] escribir(DocumentoExportable documento, Rotulos rotulos) {
        int columnas = documento.tablas().stream().mapToInt(t -> t.columnas().size()).max().orElse(1);
        Rectangle pagina = columnas > COLUMNAS_PARA_APAISAR ? PageSize.A4.rotate() : PageSize.A4;

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document pdf = new Document(pagina, 40, 40, 44, 48);
        PdfWriter escritor = PdfWriter.getInstance(pdf, salida);
        escritor.setPageEvent(new PieDePagina(documento.titulo(), rotulos));
        pdf.addTitle(documento.titulo());
        pdf.addCreator("Chaquena");
        pdf.open();

        pdf.add(new Paragraph(documento.titulo(), TITULO));
        Paragraph bajada = new Paragraph(documento.bajada(), BAJADA);
        bajada.setSpacingAfter(6);
        pdf.add(bajada);

        for (Tabla tabla : documento.tablas()) {
            if (!tabla.titulo().equals(documento.titulo())) {
                Paragraph subtitulo = new Paragraph(tabla.titulo(), SUBTITULO);
                subtitulo.setSpacingBefore(14);
                subtitulo.setSpacingAfter(6);
                pdf.add(subtitulo);
            } else {
                pdf.add(new Paragraph(" ", BAJADA));
            }
            pdf.add(tablaPdf(tabla, rotulos));
        }

        pdf.close();
        return salida.toByteArray();
    }

    private static PdfPTable tablaPdf(Tabla tabla, Rotulos rotulos) {
        List<Columna> columnas = tabla.columnas();
        PdfPTable pdf = new PdfPTable(columnas.size());
        pdf.setWidthPercentage(100);
        pdf.setHeaderRows(1);
        pdf.setWidths(pesos(columnas));

        for (Columna columna : columnas) {
            PdfPCell celda = celda(columna.nombre(), CABECERA, columna.tipo());
            celda.setBackgroundColor(BORGONA);
            celda.setBorderColor(BORGONA);
            pdf.addCell(celda);
        }

        if (tabla.filas().isEmpty()) {
            PdfPCell vacio = celda(rotulos.texto("sinDatos"), BAJADA, TipoCelda.TEXTO);
            vacio.setColspan(columnas.size());
            pdf.addCell(vacio);
        }

        boolean cebra = false;
        for (List<Object> fila : tabla.filas()) {
            for (int i = 0; i < columnas.size(); i++) {
                TipoCelda tipo = columnas.get(i).tipo();
                PdfPCell celda = celda(texto(fila.get(i), tipo, rotulos), CUERPO, tipo);
                if (cebra) {
                    celda.setBackgroundColor(CEBRA);
                }
                pdf.addCell(celda);
            }
            cebra = !cebra;
        }

        if (tabla.pie() != null) {
            for (int i = 0; i < columnas.size(); i++) {
                TipoCelda tipo = columnas.get(i).tipo();
                PdfPCell celda = celda(texto(tabla.pie().get(i), tipo, rotulos), PIE, tipo);
                celda.setBorder(Rectangle.TOP);
                celda.setBorderWidthTop(1f);
                celda.setBorderColorTop(TINTA);
                pdf.addCell(celda);
            }
        }
        return pdf;
    }

    private static PdfPCell celda(String texto, Font fuente, TipoCelda tipo) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setPadding(5);
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColor(LINEA);
        celda.setHorizontalAlignment(tipo.esNumero() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        return celda;
    }

    /** El texto se lleva lo que sobra; las cifras y las fechas, lo justo. */
    private static float[] pesos(List<Columna> columnas) {
        float[] pesos = new float[columnas.size()];
        for (int i = 0; i < pesos.length; i++) {
            pesos[i] = switch (columnas.get(i).tipo()) {
                case TEXTO -> 3f;
                case FECHA_HORA -> 2.4f;
                default -> 1.6f;
            };
        }
        return pesos;
    }

    private static String texto(Object valor, TipoCelda tipo, Rotulos rotulos) {
        return switch (valor) {
            case null -> "";
            case String s -> s;
            case Number n when tipo == TipoCelda.SOLES -> rotulos.soles(n);
            case Number n when tipo == TipoCelda.ENTERO -> rotulos.numero(n, 0, 0);
            case Number n -> rotulos.numero(n, 0, 3);
            case LocalDate d -> rotulos.fecha(d);
            case ZonedDateTime z -> rotulos.fechaHora(z);
            default -> valor.toString();
        };
    }

    /** "Chaquena · Ventas" a la izquierda y el numero de pagina a la derecha. */
    private static final class PieDePagina extends PdfPageEventHelper {
        private final String titulo;
        private final Rotulos rotulos;

        PieDePagina(String titulo, Rotulos rotulos) {
            this.titulo = titulo;
            this.rotulos = rotulos;
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            float y = documento.bottom() - 20;
            ColumnText.showTextAligned(escritor.getDirectContent(), Element.ALIGN_LEFT,
                    new Phrase("Chaquena · " + titulo, BAJADA), documento.left(), y, 0);
            ColumnText.showTextAligned(escritor.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase(rotulos.texto("pagina", escritor.getPageNumber()), BAJADA), documento.right(), y, 0);
        }
    }
}
