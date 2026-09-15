package com.chaquena.backend_logistica.archivos.service;

import java.util.Optional;

/**
 * Que imagenes se aceptan y como se reconocen. Sin Spring ni base de datos,
 * para poder probarlo con bytes sueltos.
 */
public final class ReglaImagen {

    /** Una foto de carta o un logo no necesitan mas, y el celular del mozo no quiere bajar mas. */
    public static final long MAX_BYTES = 2L * 1024 * 1024;

    public enum Tipo {
        WEBP("image/webp"),
        PNG("image/png"),
        JPEG("image/jpeg");

        private final String contenido;

        Tipo(String contenido) {
            this.contenido = contenido;
        }

        public String contenido() {
            return contenido;
        }
    }

    private ReglaImagen() {
    }

    /**
     * El tipo segun la firma de los primeros bytes. SVG no esta: es texto que
     * puede llevar scripts, y servido desde el mismo origen se ejecutaria.
     */
    public static Optional<Tipo> detectar(byte[] bytes) {
        if (bytes == null) {
            return Optional.empty();
        }
        if (empiezaCon(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(Tipo.PNG);
        }
        if (empiezaCon(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(Tipo.JPEG);
        }
        if (empiezaCon(bytes, 0, 'R', 'I', 'F', 'F') && empiezaCon(bytes, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(Tipo.WEBP);
        }
        return Optional.empty();
    }

    private static boolean empiezaCon(byte[] bytes, int desde, int... firma) {
        if (bytes.length < desde + firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((bytes[desde + i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
