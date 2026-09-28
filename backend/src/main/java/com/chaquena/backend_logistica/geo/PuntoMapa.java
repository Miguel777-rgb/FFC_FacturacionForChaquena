package com.chaquena.backend_logistica.geo;

/**
 * La regla que comparten todas las direcciones con punto en el mapa: la del
 * local, la habitual del cliente y la de cada delivery.
 */
public final class PuntoMapa {

    private PuntoMapa() {
    }

    /**
     * Van las dos coordenadas o ninguna. Ninguna es legitimo —la direccion se
     * escribio a mano, sin tocar el mapa—; media coordenada no es un punto.
     */
    public static void exigirPar(Double latitud, Double longitud, String deQue) {
        if ((latitud == null) != (longitud == null)) {
            throw new IllegalArgumentException(
                    "El punto de " + deQue + " lleva latitud y longitud, o ninguna");
        }
    }
}
