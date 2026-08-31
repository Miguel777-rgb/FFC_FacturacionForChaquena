package com.chaquena.backend_logistica.clientes.service;

import com.chaquena.backend_logistica.clientes.dto.EmpresaRequestDto;
import com.chaquena.backend_logistica.clientes.dto.EmpresaResponseDto;
import com.chaquena.backend_logistica.clientes.dto.VincularEmpresaRequestDto;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface EmpresaService {
    EmpresaResponseDto crear(EmpresaRequestDto request);
    PageResponseDto<EmpresaResponseDto> listar(String razonSocial, Pageable pageable);
    EmpresaResponseDto obtenerPorRuc(String ruc);
    List<EmpresaResponseDto> empresasDelCliente(UUID clienteId);
    EmpresaResponseDto vincularConCliente(UUID clienteId, VincularEmpresaRequestDto request);
}
