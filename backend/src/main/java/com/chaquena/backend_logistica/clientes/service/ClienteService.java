package com.chaquena.backend_logistica.clientes.service;

import com.chaquena.backend_logistica.clientes.dto.*;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ClienteService {
    List<ClienteResponseDto> buscar(String termino);
    PageResponseDto<ClienteResponseDto> listar(Pageable pageable);
    ClienteResponseDto crear(ClienteRequestDto request);
    ClienteResponseDto crearAnonimo(ClienteAnonimoRequestDto request);
    ClienteResponseDto obtenerPorId(UUID id);
    ClienteResponseDto actualizar(UUID id, ClienteRequestDto request);
    PreferenciasClienteDto preferencias(UUID id);
    ClienteResponseDto cambiarBloqueoFraude(UUID id, boolean bloqueado, String motivo);
}
