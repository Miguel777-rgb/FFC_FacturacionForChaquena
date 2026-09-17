package com.chaquena.backend_logistica.inventario.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Cuando dos nombres de la carta son el mismo.
 *
 * <p>La foto dice «ARROZ CHAUFA», la seccion sembrada se llama «Arroz Chaufa»
 * y alguien escribio a mano «Arroz chaufa ». Comparar el texto tal cual crearia
 * tres secciones; se compara la clave: sin tildes, sin mayusculas, sin signos y
 * con un solo espacio entre palabras.
 */
public final class ReglaNombresCarta {

    private ReglaNombresCarta() {
    }

    /** «Menú del Día» y «MENU DEL DIA» dan la misma clave: {@code menu del dia}. */
    public static String clave(String nombre) {
        if (nombre == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    /** Recorta y junta los espacios, sin tocar tildes ni mayusculas. Nulo si no queda nada. */
    public static String limpio(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }
}
