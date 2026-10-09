package com.chaquena.backend_logistica.archivos.service;

import com.chaquena.backend_logistica.archivos.domain.Archivo;
import com.chaquena.backend_logistica.archivos.repository.ArchivoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

/**
 * Al arrancar, pasa a WebP las imagenes que se subieron antes de que se
 * convirtiera todo.
 *
 * <p>Cada una en su sitio y con el mismo id: los platillos y el logo apuntan al
 * id, asi que siguen mostrando la misma foto. El fichero nuevo se escribe al
 * lado y se mueve encima del viejo de un solo paso; la fila se actualiza
 * despues. Si algo falla entre medias, el fichero ya es WebP y el siguiente
 * arranque solo corrige la fila. Una imagen que no se puede convertir se queda
 * como estaba, y se vuelve a intentar en el proximo arranque.
 */
@Slf4j
@Component
public class MigracionWebp {

    private static final String WEBP = ReglaImagen.Tipo.WEBP.contenido();

    private final ArchivoRepository archivoRepository;
    private final ConversorWebp conversor;
    private final ComprobacionCarpetaArchivos carpeta;
    private final Path directorio;

    public MigracionWebp(ArchivoRepository archivoRepository, ConversorWebp conversor,
            ComprobacionCarpetaArchivos carpeta, @Value("${app.archivos.directorio:./archivos}") String directorio) {
        this.archivoRepository = archivoRepository;
        this.conversor = conversor;
        this.carpeta = carpeta;
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    /** Despues de la comprobacion de la carpeta, que dice por que no se puede escribir. */
    @Order(1)
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        List<Archivo> pendientes = archivoRepository.findByTipoContenidoNot(WEBP);
        if (pendientes.isEmpty()) {
            return;
        }
        if (!carpeta.sePuedeEscribir()) {
            log.warn("{} imagenes siguen sin pasar a WebP: no se puede escribir en la carpeta.", pendientes.size());
            return;
        }
        if (!conversor.disponible()) {
            log.warn("{} imagenes siguen sin pasar a WebP: no se encontro cwebp.", pendientes.size());
            return;
        }

        int convertidas = 0;
        long antes = 0;
        long despues = 0;
        for (Archivo archivo : pendientes) {
            Optional<long[]> tamanos = convertir(archivo);
            if (tamanos.isPresent()) {
                convertidas++;
                antes += tamanos.get()[0];
                despues += tamanos.get()[1];
            }
        }
        log.info("Imagenes pasadas a WebP: {} de {} ({} KB -> {} KB).",
                convertidas, pendientes.size(), antes / 1024, despues / 1024);
    }

    /** Los bytes de antes y de despues si quedo en WebP; vacio si se dejo como estaba. */
    Optional<long[]> convertir(Archivo archivo) {
        Path ruta = directorio.resolve(archivo.getId().toString());
        try {
            byte[] actual = Files.readAllBytes(ruta);
            ReglaImagen.Tipo tipo = ReglaImagen.detectar(actual).orElse(null);
            if (tipo == null) {
                log.warn("La imagen {} no es WebP, PNG ni JPEG: se deja como esta.", archivo.getId());
                return Optional.empty();
            }

            byte[] webp = actual;
            if (tipo != ReglaImagen.Tipo.WEBP) {
                webp = conversor.aWebp(actual, tipo).orElse(null);
                if (webp == null) {
                    return Optional.empty();
                }
                reemplazar(ruta, webp, archivo);
            }
            // Si ya era WebP en disco, un arranque anterior movio el fichero y no
            // llego a la fila: solo falta ponerla al dia.
            archivo.setTipoContenido(WEBP);
            archivo.setTamanoBytes((long) webp.length);
            archivo.setModifiedBy("SYSTEM");
            archivoRepository.save(archivo);
            return Optional.of(new long[] {actual.length, webp.length});
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo pasar a WebP la imagen {}: {}", archivo.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /** Escribe al lado y mueve encima: quien lee la imagen ve la vieja o la nueva, nunca media. */
    private void reemplazar(Path ruta, byte[] webp, Archivo archivo) throws IOException {
        Path temporal = Files.createTempFile(directorio, archivo.getId().toString(), ".webp");
        try {
            Files.write(temporal, webp);
            Files.move(temporal, ruta, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }
}
