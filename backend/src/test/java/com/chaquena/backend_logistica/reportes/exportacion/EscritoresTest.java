package com.chaquena.backend_logistica.reportes.exportacion;

import com.chaquena.backend_logistica.pedidos.domain.TipoPagoEnum;
import com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable.Columna;
import com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable.Tabla;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable.TipoCelda.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EscritoresTest {

    private final Rotulos rotulos = Rotulos.para(Locale.forLanguageTag("es"));

    private DocumentoExportable documento() {
        Tabla porMetodo = new Tabla("Por método de pago",
                List.of(new Columna("Método", TEXTO), new Columna("Pagos", ENTERO), new Columna("Total", SOLES)),
                List.of(Arrays.asList("Efectivo", 12L, new BigDecimal("1250.50")),
                        Arrays.asList("Tarjeta", 3L, null)),
                Arrays.asList("Total", 15L, new BigDecimal("1250.50")));
        Tabla porDia = new Tabla("Por día",
                List.of(new Columna("Fecha", FECHA), new Columna("Total", SOLES)),
                List.of(),
                null);
        return new DocumentoExportable("Ventas", "Del 01/09/2026 al 16/09/2026", List.of(porMetodo, porDia));
    }

    @Test
    void elXlsxLlevaUnaHojaPorTablaYLasCifrasComoNumeros() throws Exception {
        byte[] xlsx = EscritorXlsx.escribir(documento(), rotulos);

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(2);
            Sheet hoja = libro.getSheet("Por método de pago");
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Ventas · Por método de pago");
            assertThat(hoja.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Método");
            assertThat(hoja.getRow(4).getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(hoja.getRow(4).getCell(2).getNumericCellValue()).isEqualTo(1250.50);
            assertThat(hoja.getRow(5).getCell(2).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(hoja.getRow(6).getCell(0).getStringCellValue()).isEqualTo("Total");

            // Una tabla vacia lo dice, en vez de dejar solo la cabecera.
            assertThat(libro.getSheet("Por día").getRow(4).getCell(0).getStringCellValue())
                    .isEqualTo("Sin datos en este rango.");
        }
    }

    @Test
    void elPdfEsUnPdfDeVerdad() throws Exception {
        byte[] pdf = EscritorPdf.escribir(documento(), rotulos);

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        PdfReader lector = new PdfReader(pdf);
        assertThat(lector.getNumberOfPages()).isEqualTo(1);
        lector.close();
    }

    @Test
    void losRotulosSiguenAlIdiomaDeLaPantalla() {
        Rotulos portugues = Rotulos.para(Locale.forLanguageTag("pt"));
        Rotulos ingles = Rotulos.para(Locale.forLanguageTag("en-US"));
        Rotulos otro = Rotulos.para(Locale.forLanguageTag("fr"));

        assertThat(portugues.valor(TipoPagoEnum.EFECTIVO)).isEqualTo("Dinheiro");
        assertThat(portugues.soles(new BigDecimal("1234.5"))).isEqualTo("S/ 1.234,50");
        assertThat(ingles.texto("rango", "a", "b")).isEqualTo("From a to b");
        assertThat(ingles.fecha(LocalDate.of(2026, 9, 16))).isEqualTo("09/16/2026");
        assertThat(otro.soles(new BigDecimal("1234.5"))).isEqualTo("S/ 1,234.50");
        assertThat(otro.valor(TipoPagoEnum.E_WALLET)).isEqualTo("Billetera");
    }

    @Test
    void unPieConOtraCantidadDeCeldasNoSeArma() {
        assertThatThrownBy(() -> new Tabla("x", List.of(new Columna("a", TEXTO)), List.of(), List.of("1", "2")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
