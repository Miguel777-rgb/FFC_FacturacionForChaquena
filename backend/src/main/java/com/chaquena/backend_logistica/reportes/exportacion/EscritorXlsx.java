package com.chaquena.backend_logistica.reportes.exportacion;

import com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable.Columna;
import com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable.Tabla;
import com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable.TipoCelda;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Escribe un {@link DocumentoExportable} como libro de Excel, una hoja por tabla.
 *
 * <p>Las cifras y las fechas van como numeros y fechas de verdad, no como
 * texto: quien abre el archivo quiere sumar una columna o filtrar por dia. El
 * separador decimal lo pone Excel segun su configuracion.
 *
 * <p>El ancho de las columnas se calcula por el largo del contenido y no con
 * {@code autoSizeColumn}, que necesita las fuentes del sistema operativo y el
 * contenedor del backend no las trae.
 */
public final class EscritorXlsx {

    private static final byte[] BORGONA = {(byte) 0xA4, 0x1E, 0x34};
    private static final int FILA_CABECERA = 3;
    private static final int ANCHO_MAXIMO = 60;

    private EscritorXlsx() {
    }

    public static byte[] escribir(DocumentoExportable documento, Rotulos rotulos) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            Set<String> nombresUsados = new HashSet<>();
            for (Tabla tabla : documento.tablas()) {
                escribirHoja(libro.createSheet(nombreDeHoja(tabla.titulo(), nombresUsados)),
                        documento, tabla, estilos, rotulos);
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir el xlsx", e);
        }
    }

    private static void escribirHoja(Sheet hoja, DocumentoExportable documento, Tabla tabla, Estilos estilos,
            Rotulos rotulos) {
        List<Columna> columnas = tabla.columnas();
        int[] anchos = new int[columnas.size()];

        // Con una sola tabla, la hoja se llama como el documento: no se repite.
        String titulo = tabla.titulo().equals(documento.titulo())
                ? documento.titulo()
                : documento.titulo() + " · " + tabla.titulo();
        celda(hoja.createRow(0), 0, titulo, estilos.titulo);
        celda(hoja.createRow(1), 0, documento.bajada(), estilos.bajada);

        Row cabecera = hoja.createRow(FILA_CABECERA);
        for (int i = 0; i < columnas.size(); i++) {
            celda(cabecera, i, columnas.get(i).nombre(), estilos.cabecera);
            anchos[i] = columnas.get(i).nombre().length();
        }

        int numeroDeFila = FILA_CABECERA + 1;
        for (List<Object> valores : tabla.filas()) {
            escribirFila(hoja.createRow(numeroDeFila++), columnas, valores, estilos.cuerpo, anchos, rotulos);
        }
        if (tabla.filas().isEmpty()) {
            celda(hoja.createRow(numeroDeFila++), 0, rotulos.texto("sinDatos"), estilos.bajada);
        } else {
            hoja.setAutoFilter(new CellRangeAddress(FILA_CABECERA, numeroDeFila - 1, 0, columnas.size() - 1));
        }
        if (tabla.pie() != null) {
            escribirFila(hoja.createRow(numeroDeFila), columnas, tabla.pie(), estilos.pie, anchos, rotulos);
        }

        hoja.createFreezePane(0, FILA_CABECERA + 1);
        for (int i = 0; i < anchos.length; i++) {
            hoja.setColumnWidth(i, Math.min(anchos[i] + 3, ANCHO_MAXIMO) * 256);
        }
    }

    private static void escribirFila(Row fila, List<Columna> columnas, List<Object> valores,
            Map<TipoCelda, CellStyle> estilos, int[] anchos, Rotulos rotulos) {
        for (int i = 0; i < columnas.size(); i++) {
            Object valor = valores.get(i);
            TipoCelda tipo = valor instanceof String ? TipoCelda.TEXTO : columnas.get(i).tipo();
            Cell celda = fila.createCell(i);
            celda.setCellStyle(estilos.get(tipo));
            switch (valor) {
                case null -> celda.setBlank();
                case Number n -> celda.setCellValue(n.doubleValue());
                case LocalDate d -> celda.setCellValue(d);
                case ZonedDateTime z -> celda.setCellValue(z.withZoneSameInstant(Rotulos.ZONA_DEL_LOCAL).toLocalDateTime());
                default -> celda.setCellValue(valor.toString());
            }
            anchos[i] = Math.max(anchos[i], largoVisible(valor, tipo, rotulos));
        }
    }

    private static int largoVisible(Object valor, TipoCelda tipo, Rotulos rotulos) {
        return switch (valor) {
            case null -> 0;
            case Number n when tipo == TipoCelda.SOLES -> rotulos.soles(n).length();
            case Number n -> rotulos.numero(n, 0, 3).length();
            case LocalDate d -> 10;
            case ZonedDateTime z -> 16;
            default -> valor.toString().length();
        };
    }

    private static void celda(Row fila, int columna, String texto, CellStyle estilo) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(texto);
        celda.setCellStyle(estilo);
    }

    /** Las hojas de Excel no pueden repetir nombre ni pasar de 31 caracteres. */
    private static String nombreDeHoja(String titulo, Set<String> usados) {
        String base = WorkbookUtil.createSafeSheetName(titulo);
        String nombre = base;
        for (int n = 2; !usados.add(nombre.toLowerCase()); n++) {
            String sufijo = " " + n;
            nombre = base.substring(0, Math.min(base.length(), 31 - sufijo.length())) + sufijo;
        }
        return nombre;
    }

    private static final class Estilos {
        final CellStyle titulo;
        final CellStyle bajada;
        final CellStyle cabecera;
        final Map<TipoCelda, CellStyle> cuerpo = new EnumMap<>(TipoCelda.class);
        final Map<TipoCelda, CellStyle> pie = new EnumMap<>(TipoCelda.class);

        Estilos(XSSFWorkbook libro) {
            XSSFFont negrita = libro.createFont();
            negrita.setBold(true);

            XSSFFont grande = libro.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 14);
            titulo = libro.createCellStyle();
            titulo.setFont(grande);

            XSSFFont tenue = libro.createFont();
            tenue.setColor(new XSSFColor(new byte[] {0x6B, 0x72, (byte) 0x80}));
            bajada = libro.createCellStyle();
            bajada.setFont(tenue);

            XSSFFont blanca = libro.createFont();
            blanca.setBold(true);
            blanca.setColor(new XSSFColor(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}));
            XSSFCellStyle estiloCabecera = libro.createCellStyle();
            estiloCabecera.setFont(blanca);
            estiloCabecera.setFillForegroundColor(new XSSFColor(BORGONA));
            estiloCabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera = estiloCabecera;

            short formatoSoles = libro.createDataFormat().getFormat("\"S/\" #,##0.00");
            short formatoEntero = libro.createDataFormat().getFormat("#,##0");
            short formatoCantidad = libro.createDataFormat().getFormat("#,##0.###");
            short formatoFecha = libro.createDataFormat().getFormat("dd/mm/yyyy");
            short formatoFechaHora = libro.createDataFormat().getFormat("dd/mm/yyyy hh:mm");

            for (TipoCelda tipo : TipoCelda.values()) {
                for (boolean esPie : new boolean[] {false, true}) {
                    CellStyle estilo = libro.createCellStyle();
                    switch (tipo) {
                        case SOLES -> estilo.setDataFormat(formatoSoles);
                        case ENTERO -> estilo.setDataFormat(formatoEntero);
                        case CANTIDAD -> estilo.setDataFormat(formatoCantidad);
                        case FECHA -> estilo.setDataFormat(formatoFecha);
                        case FECHA_HORA -> estilo.setDataFormat(formatoFechaHora);
                        case TEXTO -> {
                        }
                    }
                    estilo.setAlignment(tipo.esNumero() ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);
                    if (esPie) {
                        estilo.setFont((Font) negrita);
                        estilo.setBorderTop(BorderStyle.THIN);
                        pie.put(tipo, estilo);
                    } else {
                        cuerpo.put(tipo, estilo);
                    }
                }
            }
        }
    }
}
