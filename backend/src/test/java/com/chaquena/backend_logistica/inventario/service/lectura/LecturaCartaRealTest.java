package com.chaquena.backend_logistica.inventario.service.lectura;

import com.chaquena.backend_logistica.inventario.dto.ComplementoLeidoDto;
import com.chaquena.backend_logistica.inventario.dto.LecturaCartaDto;
import com.chaquena.backend_logistica.inventario.dto.PlatilloLeidoDto;
import com.chaquena.backend_logistica.inventario.dto.SeccionLeidaDto;
import com.chaquena.backend_logistica.inventario.service.impl.LecturaCartaServiceImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lee las fotos de la carta que haya en {@code Documentacion/carta/} con el
 * Tesseract de la maquina e imprime el resultado. No comprueba nada: sirve para
 * mirar a ojo como lee la carta real al tocar las reglas. Se salta si no hay
 * fotos o si Tesseract no tiene el espanol ({@code CARTA_TESSDATA} apunta a otra
 * carpeta de modelos).
 */
class LecturaCartaRealTest {

    @Test
    void leeLasFotosDeLaCarta() throws Exception {
        Path carpeta = Path.of("..", "Documentacion", "carta");
        Assumptions.assumeTrue(Files.isDirectory(carpeta), "sin fotos de carta");
        Tesseract tesseract = new Tesseract("tesseract", System.getenv().getOrDefault("CARTA_TESSDATA", ""));
        Assumptions.assumeTrue(tesseract.estado().disponible(), "sin Tesseract en espanol");

        LecturaCartaServiceImpl servicio = new LecturaCartaServiceImpl(tesseract, null, null, null);
        try (var fotos = Files.list(carpeta)) {
            for (Path foto : fotos.filter(p -> p.toString().endsWith(".jpg")).sorted().toList()) {
                long inicio = System.currentTimeMillis();
                LecturaCartaDto lectura = servicio.leer(new MockMultipartFile("imagen", foto.getFileName().toString(),
                        "image/jpeg", Files.readAllBytes(foto)));
                System.out.printf("%n===== %s (%d ms, %d dudosos)%n", foto.getFileName(),
                        System.currentTimeMillis() - inicio, lectura.getDudosos());
                for (SeccionLeidaDto seccion : lectura.getSecciones()) {
                    System.out.println("## " + seccion.getNombre());
                    for (PlatilloLeidoDto p : seccion.getPlatillos()) {
                        System.out.printf("  %s%-45s %7s %s| %s%n", p.isDudoso() ? "? " : "  ", p.getNombre(),
                                p.getPrecio(), p.isVegetariano() ? "[V] " : "", p.getDescripcion());
                    }
                }
                for (ComplementoLeidoDto c : lectura.getComplementos()) {
                    System.out.printf("  + %s%s %s (%s)%n", c.isDudoso() ? "? " : "", c.getNombre(), c.getPrecio(), c.getTipo());
                }
            }
        }
    }
}
