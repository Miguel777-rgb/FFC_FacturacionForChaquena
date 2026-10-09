package com.chaquena.backend_logistica.archivos.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Al arrancar, comprueba que se puede escribir en la carpeta de las imagenes.
 *
 * <p>Sin esto, una carpeta con otro dueno no se nota hasta que alguien sube una
 * foto y recibe un 500. Paso de verdad: el volumen de fotos se creo con una
 * imagen cuyo usuario tenia el uid 100, la siguiente corre con el 999, y toda
 * subida fallaba sin que el arranque dijera nada. El aviso dice los dos uid y
 * el comando que lo arregla.
 */
@Slf4j
@Component
public class ComprobacionCarpetaArchivos {

    private final Path directorio;

    public ComprobacionCarpetaArchivos(@Value("${app.archivos.directorio:./archivos}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (!sePuedeEscribir()) {
            log.error("No se puede escribir en {} (es del uid {} y el backend corre con el uid {}): ninguna "
                            + "foto ni logo se va a poder subir. Se arregla una vez, dandole la carpeta al usuario "
                            + "de la aplicacion: docker exec -u root <contenedor> chown -R chaquena:chaquena {}",
                    directorio, uidDe(directorio), uidDe(Path.of("/proc/self")), directorio);
        }
    }

    /** Si la carpeta existe, o se puede crear, y el proceso puede escribir en ella. */
    public boolean sePuedeEscribir() {
        try {
            Files.createDirectories(directorio);
        } catch (IOException e) {
            return false;
        }
        return Files.isWritable(directorio);
    }

    /**
     * El uid del dueno de la ruta, o «?» donde el sistema de archivos no lo
     * dice. El del proceso sale de {@code /proc/self}, que es suyo.
     */
    static String uidDe(Path ruta) {
        try {
            return String.valueOf(Files.getAttribute(ruta, "unix:uid"));
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException | SecurityException e) {
            return "?";
        }
    }
}
