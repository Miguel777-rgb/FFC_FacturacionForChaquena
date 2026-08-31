package com.chaquena.backend_logistica.clientes.service.impl;

import com.chaquena.backend_logistica.clientes.domain.Cliente;
import com.chaquena.backend_logistica.clientes.domain.ClienteEmpresa;
import com.chaquena.backend_logistica.clientes.domain.Empresa;
import com.chaquena.backend_logistica.clientes.dto.EmpresaRequestDto;
import com.chaquena.backend_logistica.clientes.dto.EmpresaResponseDto;
import com.chaquena.backend_logistica.clientes.dto.VincularEmpresaRequestDto;
import com.chaquena.backend_logistica.clientes.repository.ClienteEmpresaRepository;
import com.chaquena.backend_logistica.clientes.repository.ClienteRepository;
import com.chaquena.backend_logistica.clientes.repository.EmpresaRepository;
import com.chaquena.backend_logistica.clientes.service.EmpresaService;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmpresaServiceImpl implements EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteEmpresaRepository clienteEmpresaRepository;

    @Override
    @Transactional
    public EmpresaResponseDto crear(EmpresaRequestDto request) {
        if (empresaRepository.existsByRuc(request.getRuc())) {
            throw new ConflictoException("Ya existe una empresa registrada con el RUC " + request.getRuc() + ".");
        }
        Empresa empresa = Empresa.builder()
                .ruc(request.getRuc())
                .razonSocial(request.getRazonSocial())
                .celular(request.getCelular())
                .direccionFiscal(request.getDireccionFiscal())
                .createdBy(UsuarioActual.username())
                .build();
        return EmpresaResponseDto.fromEntity(empresaRepository.save(empresa));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<EmpresaResponseDto> listar(String razonSocial, Pageable pageable) {
        if (razonSocial != null && !razonSocial.isBlank()) {
            return PageResponseDto.de(
                    empresaRepository.findByRazonSocialContainingIgnoreCase(razonSocial.trim(), pageable),
                    EmpresaResponseDto::fromEntity);
        }
        return PageResponseDto.de(empresaRepository.findAll(pageable), EmpresaResponseDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public EmpresaResponseDto obtenerPorRuc(String ruc) {
        return empresaRepository.findByRuc(ruc)
                .map(EmpresaResponseDto::fromEntity)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay ninguna empresa registrada con el RUC " + ruc + "."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmpresaResponseDto> empresasDelCliente(UUID clienteId) {
        return clienteEmpresaRepository.findByPersonaId(clienteId).stream()
                .map(vinculo -> EmpresaResponseDto.fromEntity(vinculo.getEmpresa()))
                .toList();
    }

    /**
     * Vincula un cliente con la empresa a cuyo nombre pide factura. Un mismo
     * cliente puede representar a varias empresas.
     */
    @Override
    @Transactional
    public EmpresaResponseDto vincularConCliente(UUID clienteId, VincularEmpresaRequestDto request) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el cliente", clienteId));
        Empresa empresa = empresaRepository.findById(request.getEmpresaId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("la empresa", request.getEmpresaId()));

        if (clienteEmpresaRepository.existsByPersonaIdAndEmpresaId(clienteId, empresa.getId())) {
            throw new ConflictoException("El cliente ya esta vinculado a esa empresa.");
        }

        clienteEmpresaRepository.save(ClienteEmpresa.builder()
                .persona(cliente)
                .empresa(empresa)
                .cargoEnEmpresa(request.getCargoEnEmpresa())
                .createdBy(UsuarioActual.username())
                .build());

        return EmpresaResponseDto.fromEntity(empresa);
    }
}
