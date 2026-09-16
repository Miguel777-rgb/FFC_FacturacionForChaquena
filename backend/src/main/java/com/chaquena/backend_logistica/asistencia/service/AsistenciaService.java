package com.chaquena.backend_logistica.asistencia.service;

import com.chaquena.backend_logistica.asistencia.dto.AsistenciaDelDiaDto;
import com.chaquena.backend_logistica.asistencia.dto.MiAsistenciaDto;

import java.time.LocalDate;
import java.util.List;

public interface AsistenciaService {

    /** Marca la entrada de quien tiene la sesion. Nadie marca por otro. */
    MiAsistenciaDto marcarEntrada();

    MiAsistenciaDto marcarSalida();

    MiAsistenciaDto mia();

    List<AsistenciaDelDiaDto> delDia(LocalDate dia);
}
