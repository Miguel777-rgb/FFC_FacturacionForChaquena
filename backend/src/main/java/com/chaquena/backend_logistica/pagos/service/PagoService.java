package com.chaquena.backend_logistica.pagos.service;

import com.chaquena.backend_logistica.pagos.dto.*;
import com.chaquena.backend_logistica.pedidos.dto.OrdenResponseDto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public interface PagoService {
    PagoResponseDto registrar(UUID ordenId, RegistrarPagoRequestDto request);
    List<PagoResponseDto> pagosDeOrden(UUID ordenId);
    PagoResponseDto confirmar(UUID pagoId);
    OrdenResponseDto alertaFraude(UUID ordenId, AlertaFraudeRequestDto request);
    ArqueoCajaDto arqueo(ZonedDateTime desde, ZonedDateTime hasta);
    List<PagoResponseDto> alertasFraude();
}
