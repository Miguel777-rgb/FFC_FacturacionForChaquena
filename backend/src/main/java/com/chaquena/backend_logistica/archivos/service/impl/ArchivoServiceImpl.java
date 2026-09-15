package com.chaquena.backend_logistica.archivos.service.impl;

import com.chaquena.backend_logistica.archivos.domain.Archivo;
import com.chaquena.backend_logistica.archivos.dto.ArchivoDto;
import com.chaquena.backend_logistica.archivos.repository.ArchivoRepository;
import com.chaquena.backend_logistica.archivos.service.ArchivoService;
import com.chaquena.backend_logistica.archivos.service.ReglaImagen;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Las imagenes en un directorio del servidor, sin servicios externos. En Docker
 * ese directorio es un volumen: reconstruir el contenedor no borra la carta.
 */
@Service
@Slf4j
public class ArchivoServiceImpl implements ArchivoService {

    private final ArchivoRepository archivoRepository;
    private final Path directorio;

    public ArchivoServiceImpl(ArchivoRepository archivoRepository,
            @Value("${app.archivos.directorio:./archivos}") String directorio) {
        this.archivoRepository = archivoRepository;
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    /**
     * Se lee entero antes de mirar nada: con el limite de 2 MB cabe en memoria,
     * y asi la firma se comprueba sobre los mismos bytes que se escriben.
     */
    @Override
    @Transactional
    public ArchivoDto subir(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("No llego ninguna imagen.");
        }
        if (archivo.getSize() > ReglaImagen.MAX_BYTES) {
            throw new IllegalArgumentException("La imagen pesa " + (archivo.getSize() / 1024)
                    + " KB y el maximo es 2 MB.");
        }

        byte[] bytes = leerBytes(archivo);
        ReglaImagen.Tipo tipo = ReglaImagen.detectar(bytes)
                .orElseThrow(() -> new IllegalArgumentException("Solo se aceptan imagenes WebP, PNG o JPEG."));

        Archivo guardado = archivoRepository.save(Archivo.builder()
                .tipoContenido(tipo.contenido())
                .tamanoBytes((long) bytes.length)
                .nombreOriginal(recortado(archivo.getOriginalFilename()))
                .createdBy(UsuarioActual.username())
                .build());

        // Si escribir falla, la excepcion deshace la fila: no queda un archivo
        // que apunta a un fichero que no existe.
        try {
            Files.createDirectories(directorio);
            Files.write(rutaDe(guardado.getId()), bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen en el servidor.", e);
        }
        return ArchivoDto.fromEntity(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public Contenido leer(UUID id) {
        Archivo archivo = obtener(id);
        Path ruta = rutaDe(id);
        if (!Files.isReadable(ruta)) {
            throw RecursoNoEncontradoException.de("la imagen", id);
        }
        return new Contenido(new FileSystemResource(ruta), archivo.getTipoContenido(), archivo.getTamanoBytes());
    }

    @Override
    @Transactional(readOnly = true)
    public Archivo obtener(UUID id) {
        return archivoRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la imagen", id));
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        archivoRepository.findById(id).ifPresent(archivo -> {
            archivoRepository.delete(archivo);
            Path ruta = rutaDe(id);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        borrarDelDisco(ruta);
                    }
                });
            } else {
                borrarDelDisco(ruta);
            }
        });
    }

    /** Un fichero que no se pudo borrar ocupa disco, pero no rompe nada: se avisa y se sigue. */
    private void borrarDelDisco(Path ruta) {
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException e) {
            log.warn("No se pudo borrar la imagen {}: {}", ruta, e.getMessage());
        }
    }

    /** El nombre es el UUID, asi que la ruta no puede salirse del directorio. */
    private Path rutaDe(UUID id) {
        return directorio.resolve(id.toString());
    }

    private byte[] leerBytes(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer la imagen.", e);
        }
    }

    private String recortado(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        String limpio = nombre.trim();
        return limpio.length() > 255 ? limpio.substring(0, 255) : limpio;
    }
}
