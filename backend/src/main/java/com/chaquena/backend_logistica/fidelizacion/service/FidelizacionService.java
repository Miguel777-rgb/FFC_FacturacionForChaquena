package com.chaquena.backend_logistica.fidelizacion.service;

import com.chaquena.backend_logistica.fidelizacion.dto.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public interface FidelizacionService {
    FeedbackResponseDto registrarFeedback(UUID ordenId, FeedbackRequestDto request);
    FeedbackResponseDto feedbackDeOrden(UUID ordenId);
    FidelizacionDto progresoDelCliente(UUID clienteId);
    List<CuponResponseDto> cuponesDelCliente(UUID clienteId);
    CuponResponseDto canjear(String codigo, UUID ordenId);
    ReporteSatisfaccionDto satisfaccion(ZonedDateTime desde, ZonedDateTime hasta);
}
