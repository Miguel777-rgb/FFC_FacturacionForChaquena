package com.chaquena.backend_logistica.inventario.service.lectura;

import java.awt.Rectangle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte las palabras de una columna de la carta en secciones, platillos y
 * adicionales. Sin Spring ni Tesseract: recibe palabras con su caja y devuelve
 * lo que entiende, asi se prueba con palabras armadas a mano.
 *
 * <p>Lo que usa es la forma de la carta, no su texto:
 * <ul>
 *   <li>un titulo es una linea corta, en mayusculas y mas alta que un nombre;</li>
 *   <li>un platillo es una linea con un numero a la derecha;</li>
 *   <li>su descripcion es la linea de letra mas chica que viene justo debajo;</li>
 *   <li>una linea de solo numeros debajo de otra de etiquetas ("2 Piernitas
 *       4 Piernitas") son las presentaciones del platillo de arriba;</li>
 *   <li>un "+5" a la derecha es un adicional, y su nombre es el texto chico que
 *       queda debajo, en la misma columna ("con Chaufa").</li>
 * </ul>
 *
 * <p>Los precios que salen de aqui son la primera lectura. Cada uno trae la caja
 * donde esta, para que el servicio la vuelva a leer con solo digitos, que es
 * donde Tesseract confunde el "11" de esta tipografia con "1" o con comillas.
 */
public final class ReglaLecturaCarta {

    static final Pattern PRECIO = Pattern.compile("^\\d{1,3}([.,]\\d{1,2})?$");
    /** Una sola cifra: "+15" es el "+1" pegado al icono de la gaseosa. */
    static final Pattern ADICIONAL = Pattern.compile("\\+\\s?(\\d)");
    private static final Pattern PARA_PERSONAS =
            Pattern.compile("(?i)\\s+para\\s+(\\d{1,2})\\s+personas?\\s*$");
    private static final Pattern NUMERO = Pattern.compile("(\\d{1,3})(?:[.,](\\d{1,2}))?");
    private static final Set<String> MINUSCULAS_EN_TITULO =
            Set.of("y", "e", "de", "del", "la", "el", "a", "con", "o", "al");

    private ReglaLecturaCarta() {
    }

    /** Un producto con precio propio, como salio de la foto. */
    public static final class Item {
        String seccion;
        String nombre;
        String descripcion;
        boolean vegetariano;
        boolean deCirculo;
        boolean dudoso;
        double confianza = 100;
        Rectangle zona;
        Rectangle cajaPrecio;
        String precioLeido;
        boolean conAdicional;
        boolean cajaInferida;
        int orden;

        public String seccion() { return seccion; }
        public String nombre() { return nombre; }
        public String descripcion() { return descripcion; }
        public boolean vegetariano() { return vegetariano; }
        public boolean deCirculo() { return deCirculo; }
        public boolean dudoso() { return dudoso; }
        public Rectangle zona() { return zona; }
        public Rectangle cajaPrecio() { return cajaPrecio; }
        public String precioLeido() { return precioLeido; }
        public boolean cajaInferida() { return cajaInferida; }
    }

    /** Un adicional con precio extra ("+5 con Chaufa"). */
    public static final class Adicional {
        String nombre;
        Rectangle zona;
        Rectangle cajaPrecio;
        String precioLeido;

        public String nombre() { return nombre; }
        public Rectangle zona() { return zona; }
        public Rectangle cajaPrecio() { return cajaPrecio; }
        public String precioLeido() { return precioLeido; }
    }

    public record Columna(List<Item> items, List<Adicional> adicionales) {
    }

    private record Linea(List<PalabraOcr> palabras) {
        int arriba() { return palabras.stream().mapToInt(PalabraOcr::arriba).min().orElse(0); }
        int abajo() { return palabras.stream().mapToInt(PalabraOcr::abajo).max().orElse(0); }
        int izquierda() { return palabras.stream().mapToInt(PalabraOcr::izquierda).min().orElse(0); }
        int derecha() { return palabras.stream().mapToInt(PalabraOcr::derecha).max().orElse(0); }

        Rectangle caja() {
            return new Rectangle(izquierda(), arriba(), derecha() - izquierda(), abajo() - arriba());
        }

        /**
         * Alto tipico de la letra: la mediana de las palabras con letras. No
         * cuentan las de una o dos letras de poca confianza, que suelen ser un
         * precio o un icono mal leidos ("T", "E"): con ellas, la linea de un
         * nombre bajaba a la altura de una descripcion.
         */
        double alto() {
            List<Integer> altos = palabras.stream()
                    .filter(p -> tieneLetras(p.texto()) && !(p.texto().length() <= 2 && p.confianza() < 80))
                    .map(PalabraOcr::alto).sorted().toList();
            if (altos.isEmpty()) {
                altos = palabras.stream().map(PalabraOcr::alto).sorted().toList();
            }
            return altos.isEmpty() ? 0 : altos.get(altos.size() / 2);
        }

        String texto() {
            return String.join(" ", palabras.stream().map(PalabraOcr::texto).toList());
        }
    }

    /** Donde empieza la franja de precios y cuanto mide la letra de un nombre. */
    private record Medidas(double xPrecio, double altoNombre) {
    }

    /**
     * @param palabras las de la columna, en coordenadas de la pagina
     * @param columna  el rectangulo de la columna
     * @param circulos los circulos de precio de toda la pagina, en las mismas coordenadas
     */
    public static Columna interpretar(List<PalabraOcr> palabras, Rectangle columna, List<Rectangle> circulos) {
        List<Linea> lineas = agrupar(palabras.stream().filter(p -> tieneLetrasODigitos(p.texto())).toList());
        double xPrecio = columna.x + columna.width * 0.72;
        Medidas medidas = new Medidas(xPrecio, altoDeNombre(lineas, xPrecio));
        double altoNombre = medidas.altoNombre();
        List<Rectangle> circulosDeColumna = circulos.stream()
                .filter(c -> columna.contains(c.getCenterX(), c.getCenterY())).toList();

        // Reparto previo. Los titulos y las lineas con precio propio quedan como
        // lineas; el resto de lo que cae alrededor de un circulo es el texto de ese
        // circulo. Los titulos se separan antes porque el de la seccion suele
        // quedar justo encima de la primera fila de circulos.
        Map<Rectangle, List<PalabraOcr>> textoDeCirculo = new LinkedHashMap<>();
        circulosDeColumna.forEach(c -> textoDeCirculo.put(c, new ArrayList<>()));
        List<Linea> restantes = new ArrayList<>();
        List<Boolean> restantesTitulo = new ArrayList<>();
        for (Linea linea : lineas) {
            boolean titulo = esTitulo(linea, medidas);
            PalabraOcr precio = tokenPrecio(linea, medidas);
            boolean precioEnCirculo = precio != null && dentroDeAlguno(precio, circulosDeColumna);
            if (titulo || circulosDeColumna.isEmpty() || (precio != null && !precioEnCirculo)) {
                restantes.add(linea);
                restantesTitulo.add(titulo);
                continue;
            }
            List<PalabraOcr> quedan = new ArrayList<>();
            for (PalabraOcr p : linea.palabras()) {
                if (dentroDeAlguno(p, circulosDeColumna)) {
                    continue; // el numero del circulo lo lee la pasada de digitos
                }
                Rectangle circulo = circuloDelTexto(p, circulosDeColumna);
                if (circulo != null) {
                    textoDeCirculo.get(circulo).add(p);
                } else {
                    quedan.add(p);
                }
            }
            Linea resto = new Linea(quedan);
            // Lo que queda sin precio entre las filas de circulos es texto de las
            // fotos o nombres lejanos: no es un platillo por su cuenta.
            if (!quedan.isEmpty() && !cercaDeCirculos(resto, circulosDeColumna)) {
                restantes.add(resto);
                restantesTitulo.add(false);
            }
        }

        List<Item> items = new ArrayList<>();
        List<Adicional> adicionales = new ArrayList<>();
        Map<Item, Adicional> adicionalDe = new LinkedHashMap<>();
        List<Rectangle> titulos = new ArrayList<>();
        Map<Rectangle, String> nombresDeTitulo = new LinkedHashMap<>();
        String seccion = null;
        Item ultimo = null;
        Item anteriorAlUltimo = null;
        Linea lineaDelUltimo = null;
        Linea nombreDelUltimo = null;
        int orden = 0;

        for (int i = 0; i < restantes.size(); i++) {
            Linea linea = restantes.get(i);
            String texto = linea.texto();

            if (restantesTitulo.get(i)) {
                seccion = tituloLimpio(texto);
                titulos.add(linea.caja());
                nombresDeTitulo.put(linea.caja(), seccion);
                ultimo = null;
                anteriorAlUltimo = null;
                continue;
            }

            PalabraOcr precio = tokenPrecio(linea, medidas);
            if (precio == null && linea.alto() >= altoNombre * 1.6) {
                continue; // un logo o un rotulo decorativo
            }

            List<PalabraOcr> numeros = linea.palabras().stream().filter(p -> PRECIO.matcher(p.texto()).matches()).toList();
            List<PalabraOcr> otras = linea.palabras().stream().filter(p -> !PRECIO.matcher(p.texto()).matches()).toList();
            boolean soloNumeros = !numeros.isEmpty()
                    && otras.size() <= (numeros.size() >= 2 ? 1 : 0)
                    && otras.stream().allMatch(p -> p.texto().length() <= 3);

            if (soloNumeros && ultimo != null && linea.arriba() - lineaDelUltimo.abajo() < altoNombre * 2.2) {
                if (numeros.size() == 1 && otras.isEmpty() && ultimo.cajaPrecio == null) {
                    ultimo.cajaPrecio = numeros.getFirst().caja();
                    ultimo.precioLeido = numeros.getFirst().texto();
                    continue;
                }
                if (numeros.size() + otras.size() >= 2) {
                    // Presentaciones. Si el ultimo "platillo" no tiene descripcion y el
                    // de antes no tiene precio, el ultimo eran las etiquetas.
                    Item base = null;
                    Linea etiquetas = null;
                    if (ultimo.descripcion == null && anteriorAlUltimo != null && anteriorAlUltimo.cajaPrecio == null) {
                        base = anteriorAlUltimo;
                        etiquetas = lineaDelUltimo;
                        items.remove(ultimo);
                        adicionales.remove(adicionalDe.remove(ultimo));
                    } else if (ultimo.cajaPrecio == null) {
                        base = ultimo;
                    }
                    if (base != null) {
                        items.remove(base);
                        List<PalabraOcr> celdas = new ArrayList<>(linea.palabras());
                        celdas.sort(Comparator.comparingInt(PalabraOcr::izquierda));
                        for (int j = 0; j < celdas.size(); j++) {
                            PalabraOcr celda = celdas.get(j);
                            String etiqueta = etiquetas == null ? null : etiquetaCercana(etiquetas, celdas, j);
                            Item variante = new Item();
                            variante.seccion = base.seccion;
                            variante.nombre = base.nombre + " (" + (etiqueta == null ? String.valueOf(j + 1) : etiqueta) + ")";
                            variante.descripcion = base.descripcion;
                            variante.vegetariano = base.vegetariano;
                            variante.conAdicional = base.conAdicional;
                            variante.confianza = Math.min(base.confianza, celda.confianza());
                            variante.dudoso = etiqueta == null || base.dudoso;
                            variante.zona = union(base.zona, linea.caja());
                            variante.cajaPrecio = celda.caja();
                            variante.precioLeido = PRECIO.matcher(celda.texto()).matches() ? celda.texto() : null;
                            variante.orden = orden++;
                            items.add(variante);
                        }
                        ultimo = null;
                        anteriorAlUltimo = null;
                        continue;
                    }
                }
            }

            if (!tieneLetras(texto)) {
                continue;
            }

            PalabraOcr adicional = linea.palabras().stream()
                    .filter(p -> p.izquierda() >= medidas.xPrecio() && ADICIONAL.matcher(p.texto()).find())
                    .findFirst().orElse(null);
            // Mas chica que la letra de los nombres y que el nombre de su platillo:
            // asi un nombre que no trajo precio no se pega como descripcion del anterior.
            boolean chica = linea.alto() < altoNombre * 0.82
                    && (nombreDelUltimo == null || linea.alto() < nombreDelUltimo.alto() * 0.85);
            boolean pegada = ultimo != null && linea.arriba() - lineaDelUltimo.abajo() < altoNombre * 1.4;

            if (precio == null && adicional == null && chica && pegada) {
                // Descripcion. Si a la derecha hay un bloque separado por un hueco
                // grande, es el rotulo del adicional ("con Chaufa"), no la descripcion.
                List<List<PalabraOcr>> tramos = tramos(linea, altoNombre * 2);
                String mas = String.join(" ", tramos.getFirst().stream().map(PalabraOcr::texto).toList());
                ultimo.descripcion = ultimo.descripcion == null ? mas : ultimo.descripcion + " " + mas;
                Adicional suyo = adicionalDe.get(ultimo);
                if (tramos.size() > 1) {
                    ultimo.conAdicional = true; // su precio va en la franja de los que tienen "+5"
                }
                if (tramos.size() > 1 && suyo != null && suyo.nombre == null) {
                    suyo.nombre = String.join(" ", tramos.subList(1, tramos.size()).stream()
                            .flatMap(List::stream).map(PalabraOcr::texto).toList());
                }
                ultimo.zona = union(ultimo.zona, linea.caja());
                lineaDelUltimo = linea;
                continue;
            }

            Item item = new Item();
            item.seccion = seccion;
            List<PalabraOcr> nombre = new ArrayList<>();
            for (PalabraOcr p : linea.palabras()) {
                if (precio != null && p.izquierda() >= precio.izquierda()) {
                    break;
                }
                if (p == adicional || (p.izquierda() >= medidas.xPrecio() && p.texto().length() <= 2)) {
                    continue; // el precio o el icono mal leidos ("T", "EA")
                }
                nombre.add(p);
            }
            limpiarNombre(item, nombre);
            item.zona = linea.caja();
            item.orden = orden++;
            if (precio != null) {
                item.cajaPrecio = precio.caja();
                item.precioLeido = precio.texto();
            }
            if (adicional != null) {
                item.conAdicional = true;
                Adicional a = new Adicional();
                Matcher m = ADICIONAL.matcher(adicional.texto());
                a.precioLeido = m.find() ? m.group(1) : null;
                a.cajaPrecio = adicional.caja();
                a.zona = new Rectangle(adicional.izquierda() - adicional.ancho() * 3, adicional.arriba() - adicional.alto(),
                        adicional.ancho() * 5, adicional.alto() * 3);
                adicionales.add(a);
                adicionalDe.put(item, a);
            }
            items.add(item);
            anteriorAlUltimo = ultimo;
            ultimo = item;
            lineaDelUltimo = linea;
            nombreDelUltimo = linea;
        }

        // Texto de logos: todo en mayusculas, sin precio y sin descripcion.
        items.removeIf(i -> i.cajaPrecio == null && i.descripcion == null && i.nombre != null
                && mayusculas(i.nombre) >= 0.8);

        completarCajasDePrecio(items);
        items.addAll(itemsDeCirculos(textoDeCirculo, titulos, nombresDeTitulo, orden));
        for (Item item : items) {
            separarPersonas(item);
            if (item.nombre == null || item.nombre.isBlank() || item.cajaPrecio == null
                    || item.confianza < 55 || !pareceTexto(item.nombre)) {
                item.dudoso = true;
            }
        }
        items.sort(Comparator.comparingInt(i -> i.orden));
        return new Columna(items, adicionales);
    }

    /**
     * El precio a partir de lo leido en la caja. En los circulos los centimos van
     * chicos y en alto, y "2⁵⁰" se lee "250": ahi un numero de tres cifras sin
     * punto son soles y centimos.
     */
    public static BigDecimal precio(String leido, boolean deCirculo) {
        if (leido == null) {
            return null;
        }
        Matcher m = NUMERO.matcher(leido.replace(" ", ""));
        if (!m.find()) {
            return null;
        }
        BigDecimal valor = m.group(2) != null
                ? new BigDecimal(m.group(1) + "." + m.group(2))
                : new BigDecimal(m.group(1));
        if (deCirculo) {
            // Los centimos chicos y en alto: "250" y "2.51" son 2.50, y "25" tambien,
            // porque el cero chico se pierde. Ninguna bebida de circulo pasa de 15.
            if (m.group(2) == null && m.group(1).length() == 3) {
                valor = valor.movePointLeft(2);
            } else if (m.group(2) == null && m.group(1).length() == 2 && valor.intValue() > 15
                    && m.group(1).endsWith("5")) {
                valor = valor.movePointLeft(1);
            }
            valor = valor.multiply(BigDecimal.TWO).setScale(0, RoundingMode.HALF_UP)
                    .divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
        }
        // Nada se vende gratis: un cero es el borde del circulo o una "o" leida como numero.
        return valor.signum() == 0 ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }

    // --- piezas ---------------------------------------------------------------

    private static List<Linea> agrupar(List<PalabraOcr> palabras) {
        Map<String, List<PalabraOcr>> porLinea = new LinkedHashMap<>();
        for (PalabraOcr p : palabras) {
            porLinea.computeIfAbsent(p.bloque() + "/" + p.parrafo() + "/" + p.linea(), k -> new ArrayList<>()).add(p);
        }
        List<Linea> lineas = new ArrayList<>();
        for (List<PalabraOcr> ps : porLinea.values()) {
            ps.sort(Comparator.comparingInt(PalabraOcr::izquierda));
            lineas.add(new Linea(ps));
        }
        lineas.sort(Comparator.comparingInt(Linea::arriba).thenComparingInt(Linea::izquierda));
        return lineas;
    }

    /**
     * El precio de la linea: el primer numero dentro de la franja de precios
     * (el ultimo 28 % de la columna) que tiene texto a su izquierda. El primero y
     * no el ultimo, porque despues del precio vienen los "+1" y los iconos. Las
     * lineas de letra chica no tienen precio: el "2" de "con 2 chorizos" no lo es.
     */
    private static PalabraOcr tokenPrecio(Linea linea, Medidas medidas) {
        if (linea.alto() < medidas.altoNombre() * 0.8) {
            return null;
        }
        boolean hayTextoAntes = false;
        for (PalabraOcr p : linea.palabras()) {
            if (hayTextoAntes && p.izquierda() >= medidas.xPrecio() && PRECIO.matcher(p.texto()).matches()) {
                return p;
            }
            if (tieneLetras(p.texto())) {
                hayTextoAntes = true;
            }
        }
        return null;
    }

    /**
     * Un titulo es corto, de palabras reales y en mayusculas: o todo en
     * mayusculas ("MENÚ DEL DÍA" va en versalitas y mide lo mismo que un nombre),
     * o casi y mas alto que un nombre ("CoRDERO"). "VISABEDOS S", que sale de los
     * logos de las tarjetas, no pasa por la letra suelta.
     */
    private static boolean esTitulo(Linea linea, Medidas medidas) {
        String texto = linea.texto();
        if (!tieneLetras(texto) || tokenPrecio(linea, medidas) != null || ADICIONAL.matcher(texto).find()
                || linea.palabras().size() > 5) {
            return false;
        }
        for (PalabraOcr p : linea.palabras()) {
            String letras = p.texto().replaceAll("[^\\p{L}]", "");
            if (letras.length() < 2 && !MINUSCULAS_EN_TITULO.contains(letras.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        long letras = texto.chars().filter(Character::isLetter).count();
        long visibles = texto.chars().filter(c -> !Character.isWhitespace(c)).count();
        double confianza = linea.palabras().stream().mapToDouble(PalabraOcr::confianza).average().orElse(0);
        boolean grande = linea.alto() >= medidas.altoNombre() * 1.12;
        // Las fajas de titulo tienen fondo de color y Tesseract les da poca
        // confianza; a un titulo grande no se le exige.
        if (letras < 4 || (double) letras / visibles < 0.85 || (!grande && confianza < 55)) {
            return false;
        }
        double proporcion = mayusculas(texto);
        return (proporcion >= 0.8 && linea.alto() >= medidas.altoNombre() * 0.5)
                || (proporcion >= 0.5 && grande);
    }

    /**
     * Alto de la letra de un nombre. Primero una estimacion con la mitad alta de
     * las lineas; despues, si hay al menos tres lineas con precio, la mediana de
     * esas: en la pagina de bebidas las marcas enormes inflarian la estimacion y
     * las guarniciones parecerian descripciones.
     */
    private static double altoDeNombre(List<Linea> lineas, double xPrecio) {
        List<Double> altos = lineas.stream().filter(l -> tieneLetras(l.texto())).map(Linea::alto).sorted().toList();
        if (altos.isEmpty()) {
            return 1;
        }
        double medio = altos.get(altos.size() / 2);
        double estimado = mediana(altos.stream().filter(a -> a >= medio).toList());
        Medidas tentativas = new Medidas(xPrecio, estimado * 0.75);
        List<Double> conPrecio = lineas.stream().filter(l -> tokenPrecio(l, tentativas) != null).map(Linea::alto).toList();
        return conPrecio.isEmpty() ? estimado : mediana(conPrecio);
    }

    private static double mayusculas(String texto) {
        long letras = texto.chars().filter(Character::isLetter).count();
        return letras == 0 ? 0 : (double) texto.chars().filter(Character::isUpperCase).count() / letras;
    }

    /** Las palabras de la linea partidas donde hay un hueco mayor que {@code hueco}. */
    private static List<List<PalabraOcr>> tramos(Linea linea, double hueco) {
        List<List<PalabraOcr>> tramos = new ArrayList<>();
        List<PalabraOcr> actual = new ArrayList<>();
        PalabraOcr anterior = null;
        for (PalabraOcr p : linea.palabras()) {
            if (anterior != null && p.izquierda() - anterior.derecha() > hueco) {
                tramos.add(actual);
                actual = new ArrayList<>();
            }
            actual.add(p);
            anterior = p;
        }
        tramos.add(actual);
        return tramos;
    }

    private static boolean dentroDeAlguno(PalabraOcr p, List<Rectangle> circulos) {
        return circulos.stream().anyMatch(c -> c.contains(p.centroX(), p.centroY()));
    }

    private static boolean cercaDeCirculos(Linea linea, List<Rectangle> circulos) {
        double centro = (linea.arriba() + linea.abajo()) / 2.0;
        return circulos.stream().anyMatch(c -> centro >= c.y - c.height * 3 && centro <= c.y + c.height);
    }

    /** El circulo cuyo texto contiene esta palabra: justo encima y alrededor del circulo. */
    private static Rectangle circuloDelTexto(PalabraOcr p, List<Rectangle> circulos) {
        Rectangle elegido = null;
        double mejor = Double.MAX_VALUE;
        for (Rectangle c : circulos) {
            Rectangle zona = new Rectangle((int) (c.x - c.width * 1.3), (int) (c.y - c.height * 2.4),
                    (int) (c.width * 3.6), (int) (c.height * 2.6));
            if (zona.contains(p.centroX(), p.centroY())) {
                double distancia = Math.hypot(p.centroX() - c.getCenterX(), p.centroY() - c.getCenterY());
                if (distancia < mejor) {
                    mejor = distancia;
                    elegido = c;
                }
            }
        }
        return elegido;
    }

    private static List<Item> itemsDeCirculos(Map<Rectangle, List<PalabraOcr>> textoDeCirculo,
            List<Rectangle> titulos, Map<Rectangle, String> nombresDeTitulo, int orden) {
        List<Item> items = new ArrayList<>();
        for (Map.Entry<Rectangle, List<PalabraOcr>> entrada : textoDeCirculo.entrySet()) {
            Rectangle c = entrada.getKey();
            List<PalabraOcr> palabras = new ArrayList<>(entrada.getValue());
            palabras.sort(Comparator.comparingInt((PalabraOcr p) -> p.arriba() / Math.max(1, p.alto()))
                    .thenComparingInt(PalabraOcr::izquierda));
            Item item = new Item();
            item.deCirculo = true;
            item.dudoso = true;
            item.seccion = titulos.stream()
                    .filter(t -> t.y + t.height <= c.y && t.x < c.x + c.width && t.x + t.width > c.x - c.width * 4)
                    .max(Comparator.comparingInt(t -> t.y))
                    .map(nombresDeTitulo::get).orElse(null);
            limpiarNombre(item, palabras.stream().filter(p -> tieneLetras(p.texto()) || p.texto().matches("\\d+.*")).toList());
            item.cajaPrecio = new Rectangle(c.x + c.width / 10, c.y + c.height / 10, c.width * 8 / 10, c.height * 8 / 10);
            item.zona = new Rectangle((int) (c.x - c.width * 3.5), (int) (c.y - c.height * 2.8),
                    (int) (c.width * 6.8), (int) (c.height * 4));
            item.orden = orden + (c.y / Math.max(1, c.height)) * 1000 + c.x;
            items.add(item);
        }
        return items;
    }

    /**
     * La columna de precios de la seccion: donde estan los precios que si se
     * leyeron. Un platillo cuyo precio no salio se relee en esa misma franja, a
     * la altura de su nombre. Los que llevan adicional tienen el precio mas a la
     * izquierda (el "+5" ocupa el borde), y usan la franja de los suyos.
     */
    private static void completarCajasDePrecio(List<Item> items) {
        List<Rectangle> conAdicional = items.stream().filter(i -> i.cajaPrecio != null && i.conAdicional && !i.deCirculo)
                .map(i -> i.cajaPrecio).toList();
        List<Rectangle> sinAdicional = items.stream().filter(i -> i.cajaPrecio != null && !i.conAdicional && !i.deCirculo)
                .map(i -> i.cajaPrecio).toList();
        for (Item item : items) {
            if (item.cajaPrecio != null || item.deCirculo) {
                continue;
            }
            List<Rectangle> referencia = item.conAdicional && conAdicional.size() >= 2 ? conAdicional : sinAdicional;
            if (referencia.size() < 2) {
                continue;
            }
            int izquierda = (int) mediana(referencia.stream().map(r -> (double) r.x).toList());
            int ancho = (int) mediana(referencia.stream().map(r -> (double) r.width).toList());
            int alto = (int) mediana(referencia.stream().map(r -> (double) r.height).toList());
            int centro = item.zona.y + Math.min(item.zona.height, alto * 2) / 2;
            item.cajaPrecio = new Rectangle(izquierda - ancho / 3, centro - alto * 2 / 3, ancho + ancho * 2 / 3, alto * 4 / 3);
            item.cajaInferida = true;
        }
    }

    /**
     * Quita del nombre lo que no es nombre: la "V" o "W" que deja el icono de
     * vegetariano, simbolos sueltos y letras sueltas de poca confianza que
     * vienen de las fotos.
     */
    private static void limpiarNombre(Item item, List<PalabraOcr> palabras) {
        List<PalabraOcr> quedan = new ArrayList<>(palabras);
        while (!quedan.isEmpty()) {
            PalabraOcr primera = quedan.getFirst();
            String t = primera.texto();
            if (t.equals("V") || t.equals("W")) {
                item.vegetariano = true;
                quedan.removeFirst();
            } else if (t.length() > 2 && (t.charAt(0) == 'W' || t.charAt(0) == 'V')
                    && Character.isUpperCase(t.charAt(1)) && Character.isLowerCase(t.charAt(2))) {
                item.vegetariano = true;
                quedan.set(0, new PalabraOcr(t.substring(1), primera.izquierda(), primera.arriba(), primera.ancho(),
                        primera.alto(), primera.confianza(), primera.bloque(), primera.parrafo(), primera.linea()));
                break;
            } else if (!tieneLetrasODigitos(t)
                    || (t.length() == 1 && Character.isUpperCase(t.charAt(0)) && quedan.size() > 1)
                    || (t.length() <= 2 && primera.confianza() < 60)
                    || (t.length() <= 2 && !tieneVocal(t) && !t.chars().allMatch(Character::isDigit))) {
                quedan.removeFirst();
            } else {
                break;
            }
        }
        while (!quedan.isEmpty() && !tieneLetrasODigitos(quedan.getLast().texto())) {
            quedan.removeLast();
        }
        item.confianza = quedan.stream().mapToDouble(PalabraOcr::confianza).min().orElse(0);
        String nombre = String.join(" ", quedan.stream().map(PalabraOcr::texto).toList())
                .replaceAll("[\"“”«»¿¡|_]", "")
                .replaceAll("\\s+([,.;:)])", "$1")
                .replaceAll("\\s+", " ")
                .trim();
        item.nombre = nombre.isEmpty() ? null : nombre;
    }

    /** "Parrilla Familiar para 4 personas" es el nombre y "Para 4 personas." abre la descripcion. */
    private static void separarPersonas(Item item) {
        if (item.nombre == null) {
            return;
        }
        Matcher m = PARA_PERSONAS.matcher(item.nombre);
        if (m.find()) {
            item.nombre = item.nombre.substring(0, m.start()).trim();
            String personas = "Para " + m.group(1) + " personas.";
            item.descripcion = item.descripcion == null ? personas : personas + " " + item.descripcion;
        }
        if (item.descripcion != null) {
            item.descripcion = item.descripcion.replaceAll("[¿¡|_]", "").replaceAll("\\s+([,.;:)])", "$1")
                    .replaceAll("\\s+", " ").trim()
                    .replaceAll("^\\p{Lu}[.,]?\\s+(?=\\p{Lu})", "");
        }
    }

    /**
     * La etiqueta de la presentacion {@code indice}: las palabras de la linea de
     * etiquetas mas cerca de ese numero que de los otros. "Megal" y "2-Mega3"
     * vuelven a ser "Mega 1" y "Mega 3".
     */
    private static String etiquetaCercana(Linea etiquetas, List<PalabraOcr> celdas, int indice) {
        List<String> partes = new ArrayList<>();
        for (PalabraOcr p : etiquetas.palabras()) {
            int cercana = 0;
            double mejor = Double.MAX_VALUE;
            for (int j = 0; j < celdas.size(); j++) {
                double d = Math.abs(p.centroX() - celdas.get(j).centroX());
                if (d < mejor) {
                    mejor = d;
                    cercana = j;
                }
            }
            if (cercana == indice && (p.confianza() >= 50 || p.texto().matches("(?i).*mega.*|\\d+"))) {
                partes.add(p.texto());
            }
        }
        String etiqueta = String.join(" ", partes)
                .replaceAll("(?i)(mega)\\s*[lI|!]", "$1 1")
                .replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "")
                .replaceAll("^\\d+\\s*-\\s*(?=\\p{L})", "")
                .replaceAll("(\\p{L})(\\d)", "$1 $2")
                .replaceAll("(\\d)(\\p{L})", "$1 $2")
                .replaceAll("\\s+", " ")
                .trim()
                // "2 Mega 3": el 2 es ruido de la etiqueta vecina si la etiqueta ya termina en cifra.
                .replaceAll("^\\d+\\s+(?=\\p{L}.*\\d$)", "");
        return etiqueta.isEmpty() ? null : etiqueta;
    }

    /** "PARRILLAS" pasa a "Parrillas" y "GALLINA Y PoLLO" a "Gallina y Pollo". */
    static String tituloLimpio(String texto) {
        String limpio = texto.replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").trim()
                .toLowerCase(Locale.forLanguageTag("es"));
        StringBuilder titulo = new StringBuilder();
        String[] palabras = limpio.split(" ");
        for (int i = 0; i < palabras.length; i++) {
            String p = palabras[i];
            if (p.isEmpty()) {
                continue;
            }
            if (!titulo.isEmpty()) {
                titulo.append(' ');
            }
            titulo.append(i > 0 && MINUSCULAS_EN_TITULO.contains(p) ? p
                    : Character.toUpperCase(p.charAt(0)) + p.substring(1));
        }
        return titulo.toString();
    }

    private static Rectangle union(Rectangle a, Rectangle b) {
        return a == null ? b : a.union(b);
    }

    static double mediana(List<Double> valores) {
        if (valores.isEmpty()) {
            return 0;
        }
        List<Double> orden = valores.stream().sorted().toList();
        return orden.get(orden.size() / 2);
    }

    static boolean tieneLetras(String texto) {
        return texto != null && texto.chars().anyMatch(Character::isLetter);
    }

    private static boolean tieneLetrasODigitos(String texto) {
        return texto != null && texto.chars().anyMatch(Character::isLetterOrDigit);
    }

    private static boolean tieneVocal(String texto) {
        return texto.toLowerCase(Locale.ROOT).matches(".*[aeiouáéíóú].*");
    }

    /** Un nombre legible: casi todo letras, cifras, espacios y la puntuacion de una carta. */
    private static boolean pareceTexto(String nombre) {
        long validos = nombre.chars()
                .filter(c -> Character.isLetterOrDigit(c) || c == ' ' || "()+.,/-%".indexOf(c) >= 0).count();
        return (double) validos / nombre.length() >= 0.96;
    }
}
