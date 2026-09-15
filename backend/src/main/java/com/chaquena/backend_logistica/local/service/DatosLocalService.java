package com.chaquena.backend_logistica.local.service;

import com.chaquena.backend_logistica.local.dto.DatosLocalDto;

import java.math.BigDecimal;
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
}
