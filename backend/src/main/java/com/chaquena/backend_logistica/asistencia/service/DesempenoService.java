package com.chaquena.backend_logistica.asistencia.service;

import com.chaquena.backend_logistica.asistencia.dto.DesempenoDto;

import java.time.LocalDate;
import java.util.UUID;

public interface DesempenoService {

    /** Sin rango, los ultimos 30 dias contando hoy. */
    DesempenoDto de(UUID trabajadorId, LocalDate desde, LocalDate hasta);
}
