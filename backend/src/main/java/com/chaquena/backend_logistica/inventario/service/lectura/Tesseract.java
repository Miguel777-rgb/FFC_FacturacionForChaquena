package com.chaquena.backend_logistica.inventario.service.lectura;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * El programa {@code tesseract}, llamado como proceso.
 *
 * <p>Se ejecuta el binario y no una libreria nativa por JNA: en el contenedor
 * basta con instalarlo, y un fallo del reconocimiento no puede tumbar la JVM.
 * Cada llamada escribe la imagen en un archivo temporal, pide la salida TSV
 * (palabra y caja) por la salida estandar y borra el archivo.
 *
 * <p>En el contenedor el modelo de espanol viene instalado. Para ejecutar el
 * backend fuera de Docker hace falta Tesseract con el espanol, o apuntar
 * {@code app.carta.tessdata} a una carpeta que tenga {@code spa.traineddata}.
 */
@Slf4j
@Component
public class Tesseract {

    static final String IDIOMA = "spa";

    private static final long SEGUNDOS_MAXIMOS = 120;

    private final String binario;
    private final String tessdata;

    public Tesseract(@Value("${app.carta.tesseract:tesseract}") String binario,
            @Value("${app.carta.tessdata:}") String tessdata) {
        this.binario = binario;
        this.tessdata = tessdata == null ? "" : tessdata.trim();
    }

    /** Si se puede leer: el programa existe y tiene el espanol. */
    public record Estado(boolean disponible, String motivo) {
    }

    public Estado estado() {
        List<String> comando = new ArrayList<>(List.of(binario, "--list-langs"));
        agregarTessdata(comando);
        try {
            String salida = ejecutar(comando, 20);
            boolean conEspanol = salida.lines().map(String::trim).anyMatch(IDIOMA::equals);
            return conEspanol
                    ? new Estado(true, null)
                    : new Estado(false, "Tesseract está instalado, pero sin el idioma español (spa).");
        } catch (RuntimeException e) {
            return new Estado(false, "Tesseract no está instalado en el servidor.");
        }
    }

    /**
     * Las palabras de la imagen.
     *
     * @param modo        modo de segmentacion de Tesseract ({@code --psm}): 4 para una
     *                    columna de texto, 6 para un bloque uniforme
     * @param listaBlanca los unicos caracteres que puede reconocer, o {@code null}
     */
    public List<PalabraOcr> leer(BufferedImage imagen, int modo, String listaBlanca) {
        Path archivo = null;
        try {
            archivo = Files.createTempFile("carta-", ".png");
            ImageIO.write(imagen, "png", archivo.toFile());
            List<String> comando = new ArrayList<>(List.of(binario, archivo.toString(), "stdout",
                    "-l", IDIOMA, "--psm", String.valueOf(modo), "-c", "tessedit_create_tsv=1"));
            if (listaBlanca != null) {
                comando.add("-c");
                comando.add("tessedit_char_whitelist=" + listaBlanca);
            }
            agregarTessdata(comando);
            return PalabraOcr.deTsv(ejecutar(comando, SEGUNDOS_MAXIMOS));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo preparar la imagen para leerla", e);
        } finally {
            if (archivo != null) {
                try {
                    Files.deleteIfExists(archivo);
                } catch (IOException e) {
                    log.debug("No se pudo borrar {}: {}", archivo, e.getMessage());
                }
            }
        }
    }

    private void agregarTessdata(List<String> comando) {
        if (!tessdata.isEmpty()) {
            comando.add("--tessdata-dir");
            comando.add(tessdata);
        }
    }

    /** La salida estandar del proceso. Falla si no termina a tiempo o sale con error. */
    private String ejecutar(List<String> comando, long segundos) {
        ProcessBuilder constructor = new ProcessBuilder(comando).redirectError(ProcessBuilder.Redirect.DISCARD);
        // Tesseract abre un hilo por nucleo; con varias lecturas a la vez se
        // pisan entre ellas y cada una tarda mas que todas en fila.
        constructor.environment().put("OMP_THREAD_LIMIT", "1");
        Process proceso;
        try {
            proceso = constructor.start();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo ejecutar " + comando.getFirst(), e);
        }
        // La salida se lee en otro hilo: leerla aqui bloquearia hasta que el
        // proceso termine, y el limite de tiempo no llegaria a aplicarse nunca.
        CompletableFuture<byte[]> salida = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try (InputStream flujo = proceso.getInputStream()) {
                salida.complete(flujo.readAllBytes());
            } catch (IOException e) {
                salida.completeExceptionally(e);
            }
        });
        try {
            if (!proceso.waitFor(segundos, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                throw new IllegalStateException("Tesseract no termino en " + segundos + " s");
            }
            byte[] bytes = salida.get(5, TimeUnit.SECONDS);
            if (proceso.exitValue() != 0) {
                throw new IllegalStateException("Tesseract termino con codigo " + proceso.exitValue());
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (ExecutionException | TimeoutException e) {
            proceso.destroyForcibly();
            throw new IllegalStateException("No se pudo leer la salida de Tesseract", e);
        } catch (InterruptedException e) {
            proceso.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lectura interrumpida", e);
        }
    }
}
