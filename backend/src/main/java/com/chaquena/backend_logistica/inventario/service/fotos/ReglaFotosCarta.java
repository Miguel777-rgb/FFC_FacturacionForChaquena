package com.chaquena.backend_logistica.inventario.service.fotos;

import com.chaquena.backend_logistica.inventario.service.ReglaNombresCarta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Que foto del catalogo le toca a un platillo, por su nombre.
 *
 * <p>El catalogo dice, para cada foto, con que nombres de la carta va. Un nombre
 * vale si aparece entero dentro del nombre del platillo, palabra por palabra y
 * comparando claves ({@link ReglaNombresCarta#clave}): «Arroz Chaufa» vale para
 * «Arroz Chaufa de Pollo», pero «Anís» no vale para «Anisado». Si valen varios,
 * gana el mas largo, que es el mas concreto: «Lomo Saltado a lo Pobre» le gana a
 * «Lomo Saltado». Un nombre que empieza con {@code =} solo vale si es el nombre
 * entero: «=Café» es la taza sola, no el café con leche.
 */
public final class ReglaFotosCarta {

    private record Nombre(String clave, String foto, boolean exacto) {
    }

    private final List<Nombre> nombres = new ArrayList<>();

    /** El catalogo como viene de catalogo.json: cada foto con sus nombres. */
    public ReglaFotosCarta(Map<String, List<String>> catalogo) {
        catalogo.forEach((foto, nombresDeLaFoto) -> {
            for (String nombre : nombresDeLaFoto) {
                boolean exacto = nombre.startsWith("=");
                String clave = ReglaNombresCarta.clave(exacto ? nombre.substring(1) : nombre);
                if (!clave.isEmpty()) {
                    nombres.add(new Nombre(clave, foto, exacto));
                }
            }
        });
    }

    /** La foto que le toca, o vacio si ningun nombre del catalogo vale para el. */
    public Optional<String> fotoPara(String platillo) {
        String clave = ReglaNombresCarta.clave(platillo);
        String conBordes = " " + clave + " ";
        Nombre elegido = null;
        for (Nombre nombre : nombres) {
            boolean vale = nombre.exacto()
                    ? clave.equals(nombre.clave())
                    : conBordes.contains(" " + nombre.clave() + " ");
            // A igual largo se queda el primero: decide el orden del catalogo.
            if (vale && (elegido == null || nombre.clave().length() > elegido.clave().length())) {
                elegido = nombre;
            }
        }
        return Optional.ofNullable(elegido).map(Nombre::foto);
    }
}
