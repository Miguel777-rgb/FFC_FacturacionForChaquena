package com.chaquena.backend_logistica.archivos.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ReglaImagenTest {

    @Test
    void reconoceWebpPngYJpegPorSusPrimerosBytes() {
        byte[] png = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0 };
        byte[] webp = { 'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P', 'V', 'P' };

        assertThat(ReglaImagen.detectar(png)).contains(ReglaImagen.Tipo.PNG);
        assertThat(ReglaImagen.detectar(jpeg)).contains(ReglaImagen.Tipo.JPEG);
        assertThat(ReglaImagen.detectar(webp)).contains(ReglaImagen.Tipo.WEBP);
    }

    @Test
    void unSvgOUnHtmlNoSonImagenAunqueElNombreDigaLoContrario() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] html = "<html><body>carta</body></html>".getBytes(StandardCharsets.UTF_8);
        byte[] riffQueNoEsWebp = { 'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'A', 'V', 'E' };

        assertThat(ReglaImagen.detectar(svg)).isEmpty();
        assertThat(ReglaImagen.detectar(html)).isEmpty();
        assertThat(ReglaImagen.detectar(riffQueNoEsWebp)).isEmpty();
    }

    @Test
    void unArchivoMasCortoQueLaFirmaNoSeReconoce() {
        assertThat(ReglaImagen.detectar(new byte[] { (byte) 0x89, 'P' })).isEmpty();
        assertThat(ReglaImagen.detectar(new byte[0])).isEmpty();
        assertThat(ReglaImagen.detectar(null)).isEmpty();
    }
}
