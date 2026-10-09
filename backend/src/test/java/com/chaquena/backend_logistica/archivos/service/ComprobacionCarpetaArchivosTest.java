package com.chaquena.backend_logistica.archivos.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ComprobacionCarpetaArchivosTest {

    @TempDir
    Path base;

    @Test
    void unaCarpetaPropiaSePuedeEscribirYUnaQueNoExisteSeCrea() {
        Path nueva = base.resolve("archivos");

        assertThat(new ComprobacionCarpetaArchivos(nueva.toString()).sePuedeEscribir()).isTrue();
        assertThat(nueva).isDirectory();
    }

    /**
     * Lo que pasa con un volumen de otro dueno: el proceso no puede escribir. Como
     * root se podria escribir igual, asi que como root la prueba no dice nada.
     */
    @Test
    void unaCarpetaDeSoloLecturaSeDetecta() throws IOException {
        assumeFalse("0".equals(ComprobacionCarpetaArchivos.uidDe(Path.of("/proc/self"))), "corriendo como root");
        Path ajena = Files.createDirectory(base.resolve("ajena"));
        Files.setPosixFilePermissions(ajena, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assertThat(new ComprobacionCarpetaArchivos(ajena.toString()).sePuedeEscribir()).isFalse();
        } finally {
            Files.setPosixFilePermissions(ajena, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void elUidSaleDelSistemaDeArchivosODaInterrogacion() {
        assertThat(ComprobacionCarpetaArchivos.uidDe(base)).matches("\\d+|\\?");
        assertThat(ComprobacionCarpetaArchivos.uidDe(base.resolve("no-existe"))).isEqualTo("?");
    }
}
