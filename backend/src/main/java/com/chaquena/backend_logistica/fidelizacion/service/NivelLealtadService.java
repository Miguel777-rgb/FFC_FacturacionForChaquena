package com.chaquena.backend_logistica.fidelizacion.service;

import com.chaquena.backend_logistica.fidelizacion.domain.NivelLealtad;
import com.chaquena.backend_logistica.fidelizacion.dto.NivelLealtadDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NivelLealtadService {

    /** De menos a mas puntos. */
    List<NivelLealtadDto> listar();

    NivelLealtadDto crear(NivelLealtadDto request);

    NivelLealtadDto actualizar(UUID id, NivelLealtadDto request);

    void eliminar(UUID id);

    /** El nivel mas alto cuyo minimo no pasa de estos puntos. Vacio si no llega al primero. */
    Optional<NivelLealtad> nivelDe(int puntos);

    /** El primer nivel que todavia no se alcanza con estos puntos. Vacio si ya esta en el mas alto. */
    Optional<NivelLealtad> siguienteA(int puntos);
}
