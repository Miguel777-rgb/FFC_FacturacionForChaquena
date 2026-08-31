package com.chaquena.backend_logistica.fidelizacion.service;

import com.chaquena.backend_logistica.fidelizacion.domain.ConfiguracionLocal;

public interface ConfiguracionService {

    /** Devuelve la configuracion, creandola con valores por defecto si es la primera vez. */
    ConfiguracionLocal obtener();

    ConfiguracionLocal actualizar(ConfiguracionLocal cambios);

    /** El "N" de la regla de fidelizacion. */
    int calificacionesParaCupon();

    int minutosObjetivoCocina();
}
