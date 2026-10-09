package com.chaquena.backend_logistica.archivos.service.impl;

import com.chaquena.backend_logistica.archivos.domain.Archivo;
import com.chaquena.backend_logistica.archivos.dto.ArchivoDto;
import com.chaquena.backend_logistica.archivos.repository.ArchivoRepository;
import com.chaquena.backend_logistica.archivos.service.ConversorWebp;
import com.chaquena.backend_logistica.archivos.service.ReglaImagen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchivoServiceImplTest {

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };
    private static final byte[] WEBP = { 'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P' };

    @Mock
    private ArchivoRepository archivoRepository;

    /** Por defecto, como sin cwebp: no convierte y se guarda la original. */
    @Mock
    private ConversorWebp conversor;

    @TempDir
    Path directorio;

    private ArchivoServiceImpl servicio;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("almacen1", null, List.of()));
        lenient().when(conversor.aWebp(any(), any())).thenReturn(Optional.empty());
        servicio = new ArchivoServiceImpl(archivoRepository, conversor, directorio.toString());
    }

    @Test
    void loQueSePuedeConvertirSeGuardaEnWebp() throws Exception {
        when(conversor.aWebp(eq(PNG), eq(ReglaImagen.Tipo.PNG))).thenReturn(Optional.of(WEBP));
        when(archivoRepository.save(any(Archivo.class))).thenAnswer(i -> {
            Archivo archivo = i.getArgument(0);
            archivo.setId(UUID.randomUUID());
            return archivo;
        });

        ArchivoDto dto = servicio.subir(new MockMultipartFile("archivo", "logo.png", "image/png", PNG));

        assertThat(dto.getTipoContenido()).isEqualTo("image/webp");
        assertThat(dto.getTamanoBytes()).isEqualTo((long) WEBP.length);
        assertThat(Files.readAllBytes(directorio.resolve(dto.getId().toString()))).isEqualTo(WEBP);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    /** Sin cwebp, como el backend corriendo fuera de Docker: se guarda tal como llego. */
    @Test
    void guardaElTipoQueDicenLosBytesYNoElQueDeclaraElNavegador() throws Exception {
        when(archivoRepository.save(any(Archivo.class))).thenAnswer(i -> {
            Archivo archivo = i.getArgument(0);
            archivo.setId(UUID.randomUUID());
            return archivo;
        });

        ArchivoDto dto = servicio.subir(new MockMultipartFile("archivo", "lomo.jpg", "image/jpeg", PNG));

        assertThat(dto.getTipoContenido()).isEqualTo("image/png");
        assertThat(dto.getTamanoBytes()).isEqualTo((long) PNG.length);
        assertThat(Files.readAllBytes(directorio.resolve(dto.getId().toString()))).isEqualTo(PNG);
    }

    @Test
    void unHtmlQueDiceSerPngNoLlegaNiALaBaseNiAlDiscoNiACwebp() throws Exception {
        MockMultipartFile disfrazado = new MockMultipartFile("archivo", "carta.png", "image/png",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> servicio.subir(disfrazado))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WebP, PNG o JPEG");
        verifyNoInteractions(archivoRepository, conversor);
        try (Stream<Path> contenido = Files.list(directorio)) {
            assertThat(contenido).isEmpty();
        }
    }

    @Test
    void unaImagenDeMasDeDosMegasSeRechazaDiciendoElMaximo() {
        byte[] grande = Arrays.copyOf(PNG, (int) ReglaImagen.MAX_BYTES + 1);

        assertThatThrownBy(() -> servicio.subir(new MockMultipartFile("archivo", "foto.png", "image/png", grande)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 MB");
        verifyNoInteractions(archivoRepository);
    }
}
