package com.chaquena.backend_logistica.local.service;

import com.chaquena.backend_logistica.local.dto.Coordenadas;
import com.chaquena.backend_logistica.local.dto.DatosLocalDto;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface DatosLocalService {

    /** Los datos y los siete dias del horario, creandolos la primera vez. */
    DatosLocalDto obtener();

    DatosLocalDto actualizar(DatosLocalDto cambios);

    /** Pone como logo una imagen ya subida y borra la anterior. */
    DatosLocalDto cambiarLogo(UUID archivoId);

    DatosLocalDto quitarLogo();

    /** La tasa que congela cada comanda al crearse. */
    BigDecimal porcentajeIgv();

    /**
     * Donde esta el local: el centro del mapa, el foco de las sugerencias de
     * direccion y el origen de la distancia al delivery. Vacio hasta que el
     * administrador lo marque en Configuracion.
     */
    Optional<Coordenadas> ubicacion();
}
