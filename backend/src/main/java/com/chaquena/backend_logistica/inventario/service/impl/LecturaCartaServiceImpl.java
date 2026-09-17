package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.archivos.service.ReglaImagen;
import com.chaquena.backend_logistica.inventario.domain.CategoriaPlatillo;
import com.chaquena.backend_logistica.inventario.domain.ComplementoPlatillo;
import com.chaquena.backend_logistica.inventario.domain.Platillo;
import com.chaquena.backend_logistica.inventario.domain.TipoComplementoEnum;
import com.chaquena.backend_logistica.inventario.dto.ComplementoImportacionDto;
import com.chaquena.backend_logistica.inventario.dto.ComplementoLeidoDto;
import com.chaquena.backend_logistica.inventario.dto.EstadoLectorCartaDto;
import com.chaquena.backend_logistica.inventario.dto.ImportacionCartaDto;
import com.chaquena.backend_logistica.inventario.dto.LecturaCartaDto;
import com.chaquena.backend_logistica.inventario.dto.PlatilloImportacionDto;
import com.chaquena.backend_logistica.inventario.dto.PlatilloLeidoDto;
import com.chaquena.backend_logistica.inventario.dto.ResultadoImportacionCartaDto;
import com.chaquena.backend_logistica.inventario.dto.SeccionImportacionDto;
import com.chaquena.backend_logistica.inventario.dto.SeccionLeidaDto;
import com.chaquena.backend_logistica.inventario.repository.CategoriaPlatilloRepository;
import com.chaquena.backend_logistica.inventario.repository.ComplementoPlatilloRepository;
import com.chaquena.backend_logistica.inventario.repository.PlatilloRepository;
import com.chaquena.backend_logistica.inventario.service.LecturaCartaService;
import com.chaquena.backend_logistica.inventario.service.ReglaNombresCarta;
import com.chaquena.backend_logistica.inventario.service.lectura.CirculosDePrecio;
import com.chaquena.backend_logistica.inventario.service.lectura.ImagenCarta;
import com.chaquena.backend_logistica.inventario.service.lectura.PalabraOcr;
import com.chaquena.backend_logistica.inventario.service.lectura.ReglaLecturaCarta;
import com.chaquena.backend_logistica.inventario.service.lectura.Tesseract;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * La lectura va en tres pasadas por foto: la pagina agrandada y partida en
 * columnas se lee entera, las zonas de precio se vuelven a leer juntas con solo
 * digitos, y los circulos amarillos se buscan por color. Lo que sale no se
 * guarda: vuelve a la pantalla para revisarlo.
 *
 * <p>La importacion reconoce lo que ya existe por nombre, sin tildes ni
 * mayusculas: un platillo que ya esta se actualiza (precio y descripcion) y uno
 * que no, se crea. Nunca borra ni mueve de seccion un platillo existente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LecturaCartaServiceImpl implements LecturaCartaService {

    /** El tope del multipart. Una foto de carta de celular re-comprimida por la pantalla pesa mucho menos. */
    static final long MAX_BYTES = 3L * 1024 * 1024;

    private static final String DIGITOS = "0123456789.,+";

    private final Tesseract tesseract;
    private final CategoriaPlatilloRepository categoriaRepository;
    private final PlatilloRepository platilloRepository;
    private final ComplementoPlatilloRepository complementoRepository;

    @Override
    public EstadoLectorCartaDto estado() {
        Tesseract.Estado estado = tesseract.estado();
        return EstadoLectorCartaDto.builder().disponible(estado.disponible()).motivo(estado.motivo()).build();
    }

    @Override
    public LecturaCartaDto leer(MultipartFile imagen) {
        Tesseract.Estado estado = tesseract.estado();
        if (!estado.disponible()) {
            throw new ConflictoException("No se puede leer la carta en este servidor: " + estado.motivo());
        }
        BufferedImage original = imagenDe(imagen);
        long inicio = System.currentTimeMillis();

        ImagenCarta.Preparada preparada = ImagenCarta.preparar(original);
        double escala = preparada.escala();
        List<Rectangle> circulos = CirculosDePrecio.detectar(original).stream()
                .map(c -> escalar(c, escala)).toList();

        List<ReglaLecturaCarta.Item> items = new ArrayList<>();
        List<ReglaLecturaCarta.Adicional> adicionales = new ArrayList<>();
        for (int[] columna : ImagenCarta.columnas(preparada.gris())) {
            Rectangle rect = new Rectangle(columna[0], 0, columna[1] - columna[0], preparada.gris().getHeight());
            List<PalabraOcr> palabras = tesseract.leer(ImagenCarta.recortar(preparada.gris(), rect), 4, null)
                    .stream().map(p -> p.desplazada(rect.x, 0)).toList();
            ReglaLecturaCarta.Columna leida = ReglaLecturaCarta.interpretar(palabras, rect, circulos);
            items.addAll(leida.items());
            adicionales.addAll(leida.adicionales());
        }

        Map<Object, String> segundaLectura = releerPrecios(preparada.gris(), items, adicionales);
        LecturaCartaDto lectura = armar(original, escala, items, adicionales, segundaLectura);
        log.info("Carta leida en {} ms: {} secciones, {} dudosos, {} circulos.",
                System.currentTimeMillis() - inicio, lectura.getSecciones().size(), lectura.getDudosos(), circulos.size());
        return lectura;
    }

    @Override
    @Transactional
    public ResultadoImportacionCartaDto importar(ImportacionCartaDto importacion) {
        String autor = UsuarioActual.username();
        ResultadoImportacionCartaDto resultado = new ResultadoImportacionCartaDto();

        Map<String, CategoriaPlatillo> categorias = new HashMap<>();
        categoriaRepository.findAll().forEach(c -> categorias.putIfAbsent(ReglaNombresCarta.clave(c.getNombre()), c));
        Map<String, Platillo> platillos = new HashMap<>();
        platilloRepository.findAll().forEach(p -> platillos.putIfAbsent(ReglaNombresCarta.clave(p.getNombre()), p));
        Map<String, ComplementoPlatillo> complementos = new HashMap<>();
        complementoRepository.findAll().forEach(c -> complementos.putIfAbsent(ReglaNombresCarta.clave(c.getNombre()), c));
        List<String> vistos = new ArrayList<>();

        for (SeccionImportacionDto seccion : Objects.requireNonNullElse(importacion.getSecciones(),
                List.<SeccionImportacionDto>of())) {
            List<PlatilloImportacionDto> filas = Objects.requireNonNullElse(seccion.getPlatillos(), List.of());
            if (filas.isEmpty()) {
                continue;
            }
            CategoriaPlatillo categoria = categoriaDe(seccion, categorias, autor, resultado);

            for (PlatilloImportacionDto fila : filas) {
                String nombre = ReglaNombresCarta.limpio(fila.getNombre());
                String clave = ReglaNombresCarta.clave(nombre);
                if (vistos.contains(clave)) {
                    continue; // la misma fila en dos fotos
                }
                vistos.add(clave);
                String descripcion = descripcionCon(fila.getDescripcion(), Boolean.TRUE.equals(fila.getVegetariano()));
                Platillo existente = platillos.get(clave);
                if (existente != null) {
                    boolean cambia = existente.getPrecioVentaBase().compareTo(fila.getPrecio()) != 0
                            || (descripcion != null && !descripcion.equals(existente.getDescripcion()));
                    if (cambia) {
                        existente.setPrecioVentaBase(fila.getPrecio());
                        if (descripcion != null) {
                            existente.setDescripcion(descripcion);
                        }
                        existente.setModifiedBy(autor);
                        platilloRepository.save(existente);
                        resultado.setPlatillosActualizados(resultado.getPlatillosActualizados() + 1);
                    } else {
                        resultado.setPlatillosSinCambios(resultado.getPlatillosSinCambios() + 1);
                    }
                    continue;
                }
                Platillo nuevo = platilloRepository.save(Platillo.builder()
                        .categoria(categoria)
                        .nombre(nombre)
                        .descripcion(descripcion)
                        .precioVentaBase(fila.getPrecio())
                        .activo(true)
                        .createdBy(autor)
                        .build());
                platillos.put(clave, nuevo);
                resultado.setPlatillosCreados(resultado.getPlatillosCreados() + 1);
            }
        }

        for (ComplementoImportacionDto fila : Objects.requireNonNullElse(importacion.getComplementos(),
                List.<ComplementoImportacionDto>of())) {
            String nombre = ReglaNombresCarta.limpio(fila.getNombre());
            String clave = ReglaNombresCarta.clave(nombre);
            ComplementoPlatillo existente = complementos.get(clave);
            if (existente != null) {
                if (existente.getPrecioAdicional().compareTo(fila.getPrecio()) != 0) {
                    existente.setPrecioAdicional(fila.getPrecio());
                    existente.setModifiedBy(autor);
                    complementoRepository.save(existente);
                    resultado.setComplementosActualizados(resultado.getComplementosActualizados() + 1);
                }
                continue;
            }
            ComplementoPlatillo nuevo = complementoRepository.save(ComplementoPlatillo.builder()
                    .nombre(nombre)
                    .tipoComplemento(fila.getTipo())
                    .precioAdicional(fila.getPrecio())
                    .activo(true)
                    .createdBy(autor)
                    .build());
            complementos.put(clave, nuevo);
            resultado.setComplementosCreados(resultado.getComplementosCreados() + 1);
        }
        return resultado;
    }

    // --- lectura --------------------------------------------------------------

    private BufferedImage imagenDe(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Falta la foto de la carta.");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("La foto pesa mas de 3 MB.");
        }
        try {
            byte[] bytes = archivo.getBytes();
            if (ReglaImagen.detectar(bytes).isEmpty()) {
                throw new IllegalArgumentException("La foto tiene que ser JPEG, PNG o WebP.");
            }
            BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(bytes));
            if (imagen == null) {
                // Java no trae lector de WebP: la pantalla convierte a JPEG antes de subir.
                throw new IllegalArgumentException("No se pudo abrir la foto. Prueba con JPEG o PNG.");
            }
            return imagen;
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo abrir la foto.", e);
        }
    }

    /**
     * Pega todas las zonas de precio en una sola imagen y la lee con solo
     * digitos. Devuelve el texto leido por cada item o adicional.
     */
    private Map<Object, String> releerPrecios(BufferedImage gris, List<ReglaLecturaCarta.Item> items,
            List<ReglaLecturaCarta.Adicional> adicionales) {
        List<Object> duenos = new ArrayList<>();
        List<Rectangle> zonas = new ArrayList<>();
        for (ReglaLecturaCarta.Item item : items) {
            if (item.cajaPrecio() != null) {
                duenos.add(item);
                zonas.add(ampliar(item.cajaPrecio(), 14, 10));
            }
        }
        for (ReglaLecturaCarta.Adicional adicional : adicionales) {
            if (adicional.cajaPrecio() != null) {
                duenos.add(adicional);
                zonas.add(ampliar(adicional.cajaPrecio(), 14, 10));
            }
        }
        Map<Object, String> lecturas = new HashMap<>();
        if (zonas.isEmpty()) {
            return lecturas;
        }
        ImagenCarta.Apilado apilado = ImagenCarta.apilarParaDigitos(gris, zonas);
        List<PalabraOcr> palabras = tesseract.leer(apilado.imagen(), 6, DIGITOS);
        for (int i = 0; i < zonas.size(); i++) {
            int[] franja = apilado.franjas().get(i);
            List<PalabraOcr> suyas = palabras.stream()
                    .filter(p -> p.centroY() >= franja[0] && p.centroY() < franja[1])
                    .sorted((a, b) -> Integer.compare(a.izquierda(), b.izquierda()))
                    .toList();
            if (suyas.isEmpty()) {
                continue;
            }
            String texto = suyas.stream().map(PalabraOcr::texto).reduce("", String::concat);
            // En los circulos los centimos van en alto y mas chicos: si la ultima
            // pieza mide menos de tres cuartos de la primera, son decimales.
            boolean deCirculo = duenos.get(i) instanceof ReglaLecturaCarta.Item item && item.deCirculo();
            if (deCirculo && suyas.size() >= 2 && suyas.getLast().alto() < suyas.getFirst().alto() * 0.75) {
                texto = suyas.subList(0, suyas.size() - 1).stream().map(PalabraOcr::texto).reduce("", String::concat)
                        + "." + suyas.getLast().texto();
            }
            lecturas.put(duenos.get(i), texto.replace("+", ""));
        }
        return lecturas;
    }

    private LecturaCartaDto armar(BufferedImage original, double escala, List<ReglaLecturaCarta.Item> items,
            List<ReglaLecturaCarta.Adicional> adicionales, Map<Object, String> segundaLectura) {
        Map<String, SeccionLeidaDto> secciones = new LinkedHashMap<>();
        int dudosos = 0;
        for (ReglaLecturaCarta.Item item : items) {
            BigDecimal segunda = ReglaLecturaCarta.precio(segundaLectura.get(item), item.deCirculo());
            BigDecimal primera = ReglaLecturaCarta.precio(item.precioLeido(), item.deCirculo());
            BigDecimal precio = segunda != null ? segunda : primera;
            // Tres cifras en un plato de carta casi siempre es un numero de mas
            // que entro en la caja: se muestra, pero para revisar.
            boolean sospechoso = !item.deCirculo() && precio != null && precio.compareTo(BigDecimal.valueOf(100)) >= 0
                    && (item.cajaInferida() || primera == null || primera.compareTo(BigDecimal.valueOf(100)) < 0);
            boolean dudoso = item.dudoso() || precio == null || sospechoso;
            if (dudoso) {
                dudosos++;
            }
            PlatilloLeidoDto platillo = PlatilloLeidoDto.builder()
                    .nombre(item.nombre())
                    .descripcion(item.descripcion())
                    .precio(precio)
                    .vegetariano(item.vegetariano())
                    .dudoso(dudoso)
                    .recorte(dudoso ? recorte(original, item.zona(), escala) : null)
                    .build();
            String clave = item.seccion() == null ? "" : ReglaNombresCarta.clave(item.seccion());
            secciones.computeIfAbsent(clave, k -> SeccionLeidaDto.builder()
                    .nombre(item.seccion()).platillos(new ArrayList<>()).build())
                    .getPlatillos().add(platillo);
        }

        Map<String, ComplementoLeidoDto> complementos = new LinkedHashMap<>();
        for (ReglaLecturaCarta.Adicional adicional : adicionales) {
            // Al reves que en los platillos, manda la primera lectura: el "+5" ya
            // se reconocio por el signo, y sin el signo la pasada de digitos
            // confunde la cruz con un 4.
            BigDecimal precio = ReglaLecturaCarta.precio(adicional.precioLeido(), false);
            if (precio == null) {
                precio = ReglaLecturaCarta.precio(segundaLectura.get(adicional), false);
            }
            String nombre = nombreDeAdicional(adicional.nombre());
            String clave = ReglaNombresCarta.clave(nombre) + "|" + precio;
            if (complementos.containsKey(clave)) {
                continue;
            }
            boolean dudoso = nombre == null || precio == null;
            complementos.put(clave, ComplementoLeidoDto.builder()
                    .nombre(nombre)
                    .precio(precio)
                    .tipo(tipoDe(nombre))
                    .dudoso(dudoso)
                    .recorte(dudoso ? recorte(original, adicional.zona(), escala) : null)
                    .build());
        }

        return LecturaCartaDto.builder()
                .secciones(new ArrayList<>(secciones.values()))
                .complementos(new ArrayList<>(complementos.values()))
                .dudosos(dudosos)
                .build();
    }

    private String recorte(BufferedImage original, Rectangle zonaEscalada, double escala) {
        if (zonaEscalada == null) {
            return null;
        }
        Rectangle zona = new Rectangle(
                (int) (zonaEscalada.x / escala) - 12, (int) (zonaEscalada.y / escala) - 8,
                (int) (zonaEscalada.width / escala) + 24, (int) (zonaEscalada.height / escala) + 16);
        return ImagenCarta.recorteComoDataUrl(original, zona);
    }

    /** "con Chaufa" pasa a "Con chaufa". */
    private static String nombreDeAdicional(String leido) {
        String limpio = ReglaNombresCarta.limpio(leido == null ? null : leido.replaceAll("[^\\p{L}\\p{N}\\s]", " "));
        if (limpio == null) {
            return null;
        }
        String minusculas = limpio.toLowerCase(Locale.forLanguageTag("es"));
        return Character.toUpperCase(minusculas.charAt(0)) + minusculas.substring(1);
    }

    private static TipoComplementoEnum tipoDe(String nombre) {
        String clave = ReglaNombresCarta.clave(nombre);
        if (clave.matches(".*(gaseosa|bebida|refresco|inca|coca|agua).*")) {
            return TipoComplementoEnum.BEBIDA;
        }
        if (clave.contains("cerveza")) {
            return TipoComplementoEnum.CERVEZA;
        }
        if (clave.contains("jugo")) {
            return TipoComplementoEnum.JUGO;
        }
        if (clave.contains("helado")) {
            return TipoComplementoEnum.HELADO;
        }
        if (clave.matches(".*(salsa|crema).*")) {
            return TipoComplementoEnum.SALSAS;
        }
        return TipoComplementoEnum.OTROS;
    }

    // --- importacion ----------------------------------------------------------

    private CategoriaPlatillo categoriaDe(SeccionImportacionDto seccion, Map<String, CategoriaPlatillo> categorias,
            String autor, ResultadoImportacionCartaDto resultado) {
        if (seccion.getCategoriaId() != null) {
            return categoriaRepository.findById(seccion.getCategoriaId())
                    .orElseThrow(() -> RecursoNoEncontradoException.de("la seccion", seccion.getCategoriaId()));
        }
        String nombre = ReglaNombresCarta.limpio(seccion.getNombreNueva());
        if (nombre == null) {
            throw new IllegalArgumentException("Hay platillos sin seccion: elige una o escribe el nombre de la nueva.");
        }
        CategoriaPlatillo existente = categorias.get(ReglaNombresCarta.clave(nombre));
        if (existente != null) {
            return existente;
        }
        CategoriaPlatillo nueva = categoriaRepository.save(CategoriaPlatillo.builder()
                .nombre(nombre)
                .createdBy(autor)
                .build());
        categorias.put(ReglaNombresCarta.clave(nombre), nueva);
        resultado.setSeccionesCreadas(resultado.getSeccionesCreadas() + 1);
        return nueva;
    }

    /** "Vegetariano." va al principio, una sola vez. */
    static String descripcionCon(String descripcion, boolean vegetariano) {
        String limpia = ReglaNombresCarta.limpio(descripcion);
        if (!vegetariano) {
            return limpia;
        }
        if (limpia == null) {
            return "Vegetariano.";
        }
        return ReglaNombresCarta.clave(limpia).startsWith("vegetariano") ? limpia : "Vegetariano. " + limpia;
    }

    private static Rectangle escalar(Rectangle r, double escala) {
        return new Rectangle((int) (r.x * escala), (int) (r.y * escala),
                (int) (r.width * escala), (int) (r.height * escala));
    }

    private static Rectangle ampliar(Rectangle r, int dx, int dy) {
        return new Rectangle(r.x - dx, r.y - dy, r.width + 2 * dx, r.height + 2 * dy);
    }
}
