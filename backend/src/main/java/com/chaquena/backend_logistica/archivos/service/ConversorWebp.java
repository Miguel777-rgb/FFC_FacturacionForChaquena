package com.chaquena.backend_logistica.archivos.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pasa las imagenes a WebP con el programa {@code cwebp}, llamado como proceso.
 *
 * <p>Igual que {@code Tesseract}: se ejecuta el binario y no una libreria
 * nativa, asi que en el contenedor basta con instalarlo y una imagen que lo
 * haga fallar no puede tumbar la JVM.
 *
 * <p>Las fotos (JPEG) van con perdida y calidad {@value #CALIDAD}, que a la
 * vista no cambia y pesa bastante menos. Los PNG, que suelen ser logos y
 * dibujos con bordes netos, van sin perdida. Los metadatos no pasan: una foto
 * hecha con el celular trae en el EXIF hasta la ubicacion donde se tomo.
 *
 * <p>Si {@code cwebp} no esta (el backend corriendo fuera de Docker) o falla,
 * devuelve vacio y quien llama guarda la imagen tal como llego.
 */
@Slf4j
@Component
public class ConversorWebp {

    static final int CALIDAD = 82;

    /** Una imagen de 2 MB tarda una fraccion de segundo; esto es solo para que nada se cuelgue. */
    private static final long SEGUNDOS_MAXIMOS = 30;

    private final String binario;

    /** Que falta cwebp se dice una vez, no con cada imagen. */
    private final AtomicBoolean faltaAvisada = new AtomicBoolean(false);

    public ConversorWebp(@Value("${app.archivos.cwebp:cwebp}") String binario) {
        this.binario = binario;
    }

    /** Si el programa esta instalado y responde. */
    public boolean disponible() {
        try {
            ejecutar(List.of(binario, "-version"));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * La imagen en WebP. Vacio si ya lo era, si no hay {@code cwebp} o si la
     * conversion fallo: en los tres casos lo que hay que guardar es la original.
     */
    public Optional<byte[]> aWebp(byte[] imagen, ReglaImagen.Tipo tipo) {
        if (tipo == ReglaImagen.Tipo.WEBP) {
            return Optional.empty();
        }
        Path entrada = null;
        Path salida = null;
        try {
            entrada = Files.createTempFile("imagen-", tipo == ReglaImagen.Tipo.PNG ? ".png" : ".jpg");
            salida = Files.createTempFile("imagen-", ".webp");
            Files.write(entrada, imagen);

            List<String> comando = new ArrayList<>(List.of(binario, "-quiet", "-metadata", "none"));
            if (tipo == ReglaImagen.Tipo.PNG) {
                comando.add("-lossless");
            } else {
                comando.addAll(List.of("-q", String.valueOf(CALIDAD)));
            }
            comando.addAll(List.of(entrada.toString(), "-o", salida.toString()));
            ejecutar(comando);

            byte[] webp = Files.readAllBytes(salida);
            if (ReglaImagen.detectar(webp).orElse(null) != ReglaImagen.Tipo.WEBP) {
                log.warn("cwebp termino bien pero no dejo un WebP: se guarda la imagen como llego.");
                return Optional.empty();
            }
            return Optional.of(webp);
        } catch (SinCwebpException e) {
            if (faltaAvisada.compareAndSet(false, true)) {
                log.warn("No se encontro {}: las imagenes se guardan como llegan, sin pasarlas a WebP.", binario);
            }
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo pasar una imagen a WebP ({}): se guarda como llego.", e.getMessage());
            return Optional.empty();
        } finally {
            borrar(entrada);
            borrar(salida);
        }
    }

    /** Falla si el programa no arranca, no termina a tiempo o sale con error. */
    private void ejecutar(List<String> comando) {
        // Sin leer la salida: -quiet no escribe nada, y descartarla evita que un
        // mensaje largo llene la tuberia y deje el proceso esperando.
        ProcessBuilder constructor = new ProcessBuilder(comando)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        Process proceso;
        try {
            proceso = constructor.start();
        } catch (IOException e) {
            throw new SinCwebpException(e);
        }
        try {
            if (!proceso.waitFor(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                throw new IllegalStateException("cwebp no termino en " + SEGUNDOS_MAXIMOS + " s");
            }
            if (proceso.exitValue() != 0) {
                throw new IllegalStateException("cwebp termino con codigo " + proceso.exitValue());
            }
        } catch (InterruptedException e) {
            proceso.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Conversion interrumpida", e);
        }
    }

    private static void borrar(Path archivo) {
        if (archivo == null) {
            return;
        }
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            log.debug("No se pudo borrar {}: {}", archivo, e.getMessage());
        }
    }

    /** El programa no se pudo ejecutar: no esta instalado o no esta en el PATH. */
    private static final class SinCwebpException extends RuntimeException {
        SinCwebpException(Throwable causa) {
            super(causa);
        }
    }
}
