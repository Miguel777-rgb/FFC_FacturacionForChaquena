package com.chaquena.backend_logistica.archivos.service;

import com.chaquena.backend_logistica.archivos.domain.Archivo;
import com.chaquena.backend_logistica.archivos.repository.ArchivoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigracionWebpTest {

    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16 };
    private static final byte[] WEBP = { 'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P' };

    @Mock
    private ArchivoRepository archivoRepository;

    @Mock
    private ConversorWebp conversor;

    @Mock
    private ComprobacionCarpetaArchivos carpeta;

    @TempDir
    Path directorio;

    private MigracionWebp migracion;

    @BeforeEach
    void preparar() {
        migracion = new MigracionWebp(archivoRepository, conversor, carpeta, directorio.toString());
    }

    @Test
    void unJpegViejoQuedaEnWebpConElMismoIdYLaFilaAlDia() throws IOException {
        Archivo foto = enDisco(JPEG, "image/jpeg");
        pendientes(foto);
        when(conversor.aWebp(JPEG, ReglaImagen.Tipo.JPEG)).thenReturn(Optional.of(WEBP));

        migracion.alArrancar();

        assertThat(Files.readAllBytes(directorio.resolve(foto.getId().toString()))).isEqualTo(WEBP);
        assertThat(foto.getTipoContenido()).isEqualTo("image/webp");
        assertThat(foto.getTamanoBytes()).isEqualTo((long) WEBP.length);
        verify(archivoRepository).save(foto);
        // Ni un temporal olvidado en la carpeta de las fotos.
        try (Stream<Path> contenido = Files.list(directorio)) {
            assertThat(contenido).hasSize(1);
        }
    }

    @Test
    void siElFicheroYaEsWebpSoloSeCorrigeLaFila() throws IOException {
        Archivo foto = enDisco(WEBP, "image/jpeg");
        pendientes(foto);

        migracion.alArrancar();

        verify(conversor, never()).aWebp(any(), any());
        assertThat(foto.getTipoContenido()).isEqualTo("image/webp");
        verify(archivoRepository).save(foto);
    }

    @Test
    void laQueNoSePuedeConvertirSeQuedaComoEstaba() throws IOException {
        Archivo foto = enDisco(JPEG, "image/jpeg");
        pendientes(foto);
        when(conversor.aWebp(JPEG, ReglaImagen.Tipo.JPEG)).thenReturn(Optional.empty());

        migracion.alArrancar();

        assertThat(Files.readAllBytes(directorio.resolve(foto.getId().toString()))).isEqualTo(JPEG);
        assertThat(foto.getTipoContenido()).isEqualTo("image/jpeg");
        verify(archivoRepository, never()).save(any());
    }

    @Test
    void sinPoderEscribirOSinCwebpNoSeTocaNada() throws IOException {
        Archivo foto = enDisco(JPEG, "image/jpeg");
        when(archivoRepository.findByTipoContenidoNot("image/webp")).thenReturn(List.of(foto));

        when(carpeta.sePuedeEscribir()).thenReturn(false);
        migracion.alArrancar();

        when(carpeta.sePuedeEscribir()).thenReturn(true);
        when(conversor.disponible()).thenReturn(false);
        migracion.alArrancar();

        verify(conversor, never()).aWebp(any(), any());
        verify(archivoRepository, never()).save(any());
        assertThat(Files.readAllBytes(directorio.resolve(foto.getId().toString()))).isEqualTo(JPEG);
    }

    private Archivo enDisco(byte[] bytes, String tipo) throws IOException {
        Archivo archivo = Archivo.builder().id(UUID.randomUUID()).tipoContenido(tipo)
                .tamanoBytes((long) bytes.length).createdBy("admin").build();
        Files.write(directorio.resolve(archivo.getId().toString()), bytes);
        return archivo;
    }

    private void pendientes(Archivo... archivos) {
        when(archivoRepository.findByTipoContenidoNot("image/webp")).thenReturn(List.of(archivos));
        when(carpeta.sePuedeEscribir()).thenReturn(true);
        when(conversor.disponible()).thenReturn(true);
    }
}
