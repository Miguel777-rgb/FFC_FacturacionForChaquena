package com.chaquena.backend_logistica.asistencia.service;

import com.chaquena.backend_logistica.asistencia.dto.TurnoDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TurnoService {

    /** Los siete dias desde {@code desde}. Sin fecha, la semana de hoy desde el lunes. */
    List<TurnoDto> semana(LocalDate desde);

    TurnoDto crear(TurnoDto request);

    TurnoDto actualizar(UUID id, TurnoDto request);

    void eliminar(UUID id);
}
