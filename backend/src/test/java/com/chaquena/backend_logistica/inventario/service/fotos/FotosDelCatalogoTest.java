package com.chaquena.backend_logistica.inventario.service.fotos;

import com.chaquena.backend_logistica.archivos.domain.Archivo;
import com.chaquena.backend_logistica.archivos.service.ArchivoService;
import com.chaquena.backend_logistica.archivos.service.ComprobacionCarpetaArchivos;
import com.chaquena.backend_logistica.archivos.service.ReglaImagen;
import com.chaquena.backend_logistica.inventario.domain.Platillo;
import com.chaquena.backend_logistica.inventario.repository.PlatilloRepository;
import com.chaquena.backend_logistica.inventario.service.ReglaNombresCarta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FotosDelCatalogoTest {

    @Mock
    private PlatilloRepository platilloRepository;

    @Mock
    private ArchivoService archivoService;

    @Mock
    private ComprobacionCarpetaArchivos carpeta;

    private FotosDelCatalogo fotos;

    @BeforeEach
    void preparar() {
        fotos = new FotosDelCatalogo(platilloRepository, archivoService, carpeta,
                TransactionOperations.withoutTransaction());
    }

    @Test
    void cadaPlatilloSinFotoRecibeSuPropiaCopia() {
        Platillo lomo = platillo("Lomo Saltado");
        Platillo saltado = platillo("Saltado de Pollo");
        sinFoto(lomo, saltado);
        when(archivoService.guardar(any(), eq("lomo-saltado.webp"))).thenAnswer(invocacion -> archivo());

        fotos.alArrancar();

        verify(archivoService, times(2)).guardar(aryEq(FotosDelCatalogo.leerFoto("lomo-saltado")),
                eq("lomo-saltado.webp"));
        assertThat(lomo.getFoto()).isNotNull();
        assertThat(saltado.getFoto()).isNotNull().isNotSameAs(lomo.getFoto());
        assertThat(lomo.getModifiedBy()).isEqualTo("SYSTEM");
        verify(platilloRepository).save(lomo);
        verify(platilloRepository).save(saltado);
    }

    @Test
    void elQueNoEstaEnElCatalogoSeQuedaSinFoto() {
        Platillo pachamanca = platillo("Pachamanca");
        sinFoto(pachamanca);

        fotos.alArrancar();

        verify(archivoService, never()).guardar(any(), any());
        assertThat(pachamanca.getFoto()).isNull();
    }

    @Test
    void siMientrasTantoLeSubieronUnaEsaManda() {
        Platillo leido = platillo("Ceviche Clásico");
        when(platilloRepository.findByFotoIsNull()).thenReturn(List.of(leido));
        when(carpeta.sePuedeEscribir()).thenReturn(true);
        Platillo actual = platillo("Ceviche Clásico");
        actual.setId(leido.getId());
        actual.setFoto(archivo());
        when(platilloRepository.findById(leido.getId())).thenReturn(Optional.of(actual));

        fotos.alArrancar();

        verify(archivoService, never()).guardar(any(), any());
        verify(platilloRepository, never()).save(any());
    }

    @Test
    void sinPoderEscribirNoSeTocaNada() {
        when(platilloRepository.findByFotoIsNull()).thenReturn(List.of(platillo("Lomo Saltado")));
        when(carpeta.sePuedeEscribir()).thenReturn(false);

        fotos.alArrancar();

        verify(archivoService, never()).guardar(any(), any());
    }

    @Test
    void unPlatilloQueFallaNoFrenaALosDemas() {
        Platillo ceviche = platillo("Ceviche Clásico");
        Platillo aji = platillo("Ají de Gallina");
        sinFoto(ceviche, aji);
        when(archivoService.guardar(any(), eq("ceviche.webp")))
                .thenThrow(new UncheckedIOException("disco lleno", new IOException()));
        when(archivoService.guardar(any(), eq("aji-de-gallina.webp"))).thenAnswer(invocacion -> archivo());

        fotos.alArrancar();

        assertThat(ceviche.getFoto()).isNull();
        assertThat(aji.getFoto()).isNotNull();
    }

    @Test
    void niUnFalloDeLaBaseImpideArrancar() {
        when(platilloRepository.findByFotoIsNull()).thenThrow(new IllegalStateException("sin conexion"));

        assertThatCode(fotos::alArrancar).doesNotThrowAnyException();
    }

    /** Una foto nueva sin su autor y su licencia no puede entrar en la carpeta. */
    @Test
    void cadaFotoDelCatalogoEstaEnLaCarpetaEnWebpYConSusCreditos() throws Exception {
        Map<String, List<String>> catalogo = FotosDelCatalogo.leerCatalogo();
        Map<String, Map<String, String>> creditos = leerCreditos();
        Set<String> enCarpeta;
        Path carpetaFotos = Path.of(getClass().getResource("/" + FotosDelCatalogo.CARPETA).toURI());
        try (Stream<Path> ficheros = Files.list(carpetaFotos)) {
            enCarpeta = ficheros.map(fichero -> fichero.getFileName().toString())
                    .filter(nombre -> nombre.endsWith(".webp"))
                    .map(nombre -> nombre.substring(0, nombre.length() - ".webp".length()))
                    .collect(Collectors.toSet());
        }

        assertThat(catalogo.keySet()).containsExactlyInAnyOrderElementsOf(enCarpeta);
        assertThat(creditos.keySet()).containsExactlyInAnyOrderElementsOf(enCarpeta);
        for (String foto : catalogo.keySet()) {
            assertThat(ReglaImagen.detectar(FotosDelCatalogo.leerFoto(foto))).as(foto)
                    .contains(ReglaImagen.Tipo.WEBP);
            assertThat(creditos.get(foto)).as(foto).containsKeys("autor", "licencia", "fuente");
        }
    }

    @Test
    void ningunNombreLlevaADosFotos() {
        Map<String, String> fotoDe = new HashMap<>();
        FotosDelCatalogo.leerCatalogo().forEach((foto, nombres) -> nombres.forEach(nombre -> {
            String clave = ReglaNombresCarta.clave(nombre.startsWith("=") ? nombre.substring(1) : nombre);
            assertThat(fotoDe.put(clave, foto)).as(nombre).isNull();
        }));
    }

    private static Map<String, Map<String, String>> leerCreditos() throws IOException {
        try (InputStream entrada = FotosDelCatalogoTest.class
                .getResourceAsStream("/" + FotosDelCatalogo.CARPETA + "creditos.json")) {
            return JsonMapper.builder().build().readValue(entrada,
                    new TypeReference<Map<String, Map<String, String>>>() {
                    });
        }
    }

    private void sinFoto(Platillo... platillos) {
        when(platilloRepository.findByFotoIsNull()).thenReturn(List.of(platillos));
        when(carpeta.sePuedeEscribir()).thenReturn(true);
        for (Platillo platillo : platillos) {
            lenient().when(platilloRepository.findById(platillo.getId())).thenReturn(Optional.of(platillo));
        }
    }

    private static Platillo platillo(String nombre) {
        return Platillo.builder().id(UUID.randomUUID()).nombre(nombre).createdBy("admin").modifiedBy("admin").build();
    }

    private static Archivo archivo() {
        return Archivo.builder().id(UUID.randomUUID()).tipoContenido("image/webp").tamanoBytes(1L)
                .createdBy("SYSTEM").build();
    }
}
