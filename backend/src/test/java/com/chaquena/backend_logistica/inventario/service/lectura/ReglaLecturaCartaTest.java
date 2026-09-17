package com.chaquena.backend_logistica.inventario.service.lectura;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las reglas se prueban con palabras armadas a mano, con las medidas de la carta
 * del local: columna de 1400 pixeles, nombres de 60 de alto, descripciones de 42
 * y el precio pegado al borde derecho.
 */
class ReglaLecturaCartaTest {

    private static final Rectangle COLUMNA = new Rectangle(0, 0, 1400, 4000);
    private static final int ALTO_NOMBRE = 60;
    private static final int ALTO_DESCRIPCION = 42;

    private final List<PalabraOcr> palabras = new ArrayList<>();
    private int linea = 0;
    private int y = 0;

    /** Una linea: cada texto se coloca uno tras otro, y el que empieza con "@" va pegado a la derecha. */
    private void linea(int alto, String... textos) {
        int x = 70;
        linea++;
        for (String texto : textos) {
            boolean derecha = texto.startsWith("@");
            String limpio = derecha ? texto.substring(1) : texto;
            int ancho = limpio.length() * 25;
            int izquierda = derecha ? 1330 : x;
            palabras.add(new PalabraOcr(limpio, izquierda, y, ancho, alto, 90, 1, linea, 1));
            x = izquierda + ancho + 20;
        }
        y += alto + 12;
    }

    /** Una linea repartida en celdas, como la grilla de presentaciones: cada celda centrada en su tramo. */
    private void grilla(int alto, String[]... celdas) {
        linea++;
        for (int i = 0; i < celdas.length; i++) {
            int centro = COLUMNA.width * (2 * i + 1) / (2 * celdas.length);
            int ancho = 0;
            for (String texto : celdas[i]) {
                ancho += texto.length() * 25 + 12;
            }
            int x = centro - (ancho - 12) / 2;
            for (String texto : celdas[i]) {
                palabras.add(new PalabraOcr(texto, x, y, texto.length() * 25, alto, 90, 1, linea, 1));
                x += texto.length() * 25 + 12;
            }
        }
        y += alto + 12;
    }

    private void salto() {
        y += 30;
    }

    private ReglaLecturaCarta.Columna interpretar() {
        return ReglaLecturaCarta.interpretar(palabras, COLUMNA, List.of());
    }

    @Test
    void unTituloUnPlatilloYSuDescripcion() {
        linea(ALTO_NOMBRE + 26, "PARRILLAS");
        linea(ALTO_NOMBRE, "Parrilla", "de", "Res", "@20");
        linea(ALTO_DESCRIPCION, "Corte", "de", "res", "con", "salchicha,", "mote", "y", "papa.");

        List<ReglaLecturaCarta.Item> items = interpretar().items();

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().seccion()).isEqualTo("Parrillas");
        assertThat(items.getFirst().nombre()).isEqualTo("Parrilla de Res");
        assertThat(items.getFirst().descripcion()).isEqualTo("Corte de res con salchicha, mote y papa.");
        assertThat(items.getFirst().dudoso()).isFalse();
    }

    @Test
    void unNumeroEnLaDescripcionNoEsUnPrecio() {
        linea(ALTO_NOMBRE, "Parrilla", "Familiar", "para", "4", "personas", "@86");
        linea(ALTO_DESCRIPCION, "2", "parrillas", "de", "cerdo", "con", "@2", "chorizos.");

        List<ReglaLecturaCarta.Item> items = interpretar().items();

        assertThat(items).hasSize(1);
        // "para 4 personas" es del plato, pero se lee mejor al principio de la descripcion.
        assertThat(items.getFirst().nombre()).isEqualTo("Parrilla Familiar");
        assertThat(items.getFirst().descripcion()).startsWith("Para 4 personas. 2 parrillas");
    }

    @Test
    void unaGrillaDePresentacionesDaUnPlatilloPorCada() {
        linea(ALTO_NOMBRE, "Piernitas", "Broaster");
        linea(ALTO_DESCRIPCION, "Piernitas", "de", "pollo", "marinadas.");
        grilla(ALTO_NOMBRE - 6, new String[] {"2", "Piernitas"}, new String[] {"4", "Piernitas"},
                new String[] {"6", "Piernitas"});
        grilla(ALTO_NOMBRE - 6, new String[] {"12"}, new String[] {"18"}, new String[] {"24"});

        List<ReglaLecturaCarta.Item> items = interpretar().items();

        assertThat(items).extracting(ReglaLecturaCarta.Item::nombre)
                .containsExactly("Piernitas Broaster (2 Piernitas)", "Piernitas Broaster (4 Piernitas)",
                        "Piernitas Broaster (6 Piernitas)");
        assertThat(items).allSatisfy(i -> assertThat(i.descripcion()).isEqualTo("Piernitas de pollo marinadas."));
        assertThat(items.getFirst().precioLeido()).isEqualTo("12");
    }

    @Test
    void elMasCincoEsUnAdicionalYSuNombreEstaDebajo() {
        linea(ALTO_NOMBRE, "Bistec", "Clásico", "14", "@+5");
        linea(ALTO_DESCRIPCION, "Corte", "de", "res", "con", "papas", "fritas.", "@con", "Chaufa");

        ReglaLecturaCarta.Columna columna = interpretar();

        assertThat(columna.adicionales()).hasSize(1);
        assertThat(columna.adicionales().getFirst().nombre()).isEqualTo("con Chaufa");
        assertThat(columna.adicionales().getFirst().precioLeido()).isEqualTo("5");
        // El rotulo de la derecha no se cuela en la descripcion del platillo.
        assertThat(columna.items().getFirst().descripcion()).isEqualTo("Corte de res con papas fritas.");
    }

    @Test
    void laVeDelIconoMarcaVegetarianoYNoQuedaEnElNombre() {
        linea(ALTO_NOMBRE, "V", "Chaufa", "de", "Verduras", "@11");

        ReglaLecturaCarta.Item item = interpretar().items().getFirst();

        assertThat(item.nombre()).isEqualTo("Chaufa de Verduras");
        assertThat(item.vegetariano()).isTrue();
    }

    @Test
    void unPlatilloSinPrecioLeidoSeQuedaConLaCajaDeSuColumna() {
        linea(ALTO_NOMBRE, "Lomo", "Saltado", "a", "lo", "Pobre", "@21");
        linea(ALTO_DESCRIPCION, "Corte", "de", "res", "salteado.");
        linea(ALTO_NOMBRE, "Lomo", "Saltado");
        linea(ALTO_DESCRIPCION, "Corte", "de", "res", "con", "papas.");
        linea(ALTO_NOMBRE, "Saltado", "de", "Pollo", "@11");

        List<ReglaLecturaCarta.Item> items = interpretar().items();

        ReglaLecturaCarta.Item sinPrecio = items.get(1);
        assertThat(sinPrecio.nombre()).isEqualTo("Lomo Saltado");
        assertThat(sinPrecio.precioLeido()).isNull();
        assertThat(sinPrecio.cajaPrecio()).isNotNull();
        assertThat(sinPrecio.cajaInferida()).isTrue();
        assertThat(sinPrecio.cajaPrecio().x).isCloseTo(1330, org.assertj.core.data.Offset.offset(40));
    }

    @Test
    void elTextoDeUnLogoNoEsUnPlatillo() {
        linea(ALTO_NOMBRE * 2, "Chaquena");
        salto();
        linea(ALTO_NOMBRE, "MENÚ", "DEL", "DÍA");
        linea(ALTO_NOMBRE, "Milanesa", "de", "Pollo", "@11");

        List<ReglaLecturaCarta.Item> items = interpretar().items();

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().seccion()).isEqualTo("Menú del Día");
    }

    @Test
    void elPrecioDeUnCirculoTraeLosCentimosEnAlto() {
        assertThat(ReglaLecturaCarta.precio("250", true)).isEqualByComparingTo("2.50");
        assertThat(ReglaLecturaCarta.precio("2.51", true)).isEqualByComparingTo("2.50");
        assertThat(ReglaLecturaCarta.precio("25", true)).isEqualByComparingTo("2.50");
        assertThat(ReglaLecturaCarta.precio("10", true)).isEqualByComparingTo("10.00");
        assertThat(ReglaLecturaCarta.precio("14", false)).isEqualByComparingTo("14.00");
        assertThat(ReglaLecturaCarta.precio("0", true)).isNull();
        assertThat(ReglaLecturaCarta.precio(null, false)).isNull();
    }

    @Test
    void elTituloSeEscribeComoUnNombreYNoEnMayusculas() {
        assertThat(ReglaLecturaCarta.tituloLimpio("GALLINA Y PoLLO")).isEqualTo("Gallina y Pollo");
        assertThat(ReglaLecturaCarta.tituloLimpio("— CAFÉ E INFUSIONES —")).isEqualTo("Café e Infusiones");
    }

    @Test
    void lasPalabrasSalenDelTsvDeTesseract() {
        String tsv = """
                level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext
                1\t1\t0\t0\t0\t0\t0\t0\t1400\t4000\t-1\t
                5\t1\t1\t2\t1\t1\t70\t100\t180\t60\t92\tParrilla
                5\t1\t1\t2\t1\t2\t1330\t105\t60\t48\t96\t20
                5\t1\t1\t2\t1\t3\t400\t100\t40\t60\t93\t
                """;
        List<PalabraOcr> palabras = PalabraOcr.deTsv(tsv);

        assertThat(palabras).hasSize(2);
        assertThat(palabras.getFirst().texto()).isEqualTo("Parrilla");
        assertThat(palabras.getLast().izquierda()).isEqualTo(1330);
        assertThat(BigDecimal.valueOf(palabras.getLast().confianza())).isEqualByComparingTo("96");
    }
}
