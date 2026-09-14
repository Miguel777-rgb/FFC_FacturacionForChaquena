package com.chaquena.backend_logistica.local.service;

import com.chaquena.backend_logistica.local.dto.DatosLocalDto;

import java.math.BigDecimal;

public interface DatosLocalService {

    /** Los datos y los siete dias del horario, creandolos la primera vez. */
    DatosLocalDto obtener();

    DatosLocalDto actualizar(DatosLocalDto cambios);

    /** La tasa que congela cada comanda al crearse. */
    BigDecimal porcentajeIgv();
}
