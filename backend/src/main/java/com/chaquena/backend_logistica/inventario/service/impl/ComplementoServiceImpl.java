package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.inventario.domain.ComplementoPlatillo;
import com.chaquena.backend_logistica.inventario.domain.Insumo;
import com.chaquena.backend_logistica.inventario.domain.TipoComplementoEnum;
import com.chaquena.backend_logistica.inventario.dto.ComplementoRequestDto;
import com.chaquena.backend_logistica.inventario.dto.ComplementoResponseDto;
import com.chaquena.backend_logistica.inventario.repository.ComplementoPlatilloRepository;
import com.chaquena.backend_logistica.inventario.repository.InsumoRepository;
import com.chaquena.backend_logistica.inventario.service.ComplementoService;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ComplementoServiceImpl implements ComplementoService {

    private final ComplementoPlatilloRepository complementoRepository;
    private final InsumoRepository insumoRepository;

    @Override
    @Transactional
    public ComplementoResponseDto crear(ComplementoRequestDto request) {
        ComplementoPlatillo complemento = ComplementoPlatillo.builder()
                .nombre(request.getNombre())
                .tipoComplemento(request.getTipoComplemento())
                .precioAdicional(request.getPrecioAdicional())
                .insumoAsociado(buscarInsumoOpcional(request.getInsumoAsociadoId()))
                .activo(request.getActivo() == null || request.getActivo())
                .createdBy(UsuarioActual.username())
                .build();
        return ComplementoResponseDto.fromEntity(complementoRepository.save(complemento));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplementoResponseDto> listar(TipoComplementoEnum tipo, boolean soloActivos) {
        List<ComplementoPlatillo> complementos;
        if (tipo != null) {
            complementos = complementoRepository.findByTipoComplementoAndActivoTrue(tipo);
        } else if (soloActivos) {
            complementos = complementoRepository.findByActivoTrue();
        } else {
            complementos = complementoRepository.findAll();
        }
        return complementos.stream().map(ComplementoResponseDto::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ComplementoResponseDto obtenerPorId(UUID id) {
        return ComplementoResponseDto.fromEntity(buscar(id));
    }

    @Override
    @Transactional
    public ComplementoResponseDto actualizar(UUID id, ComplementoRequestDto request) {
        ComplementoPlatillo complemento = buscar(id);
        complemento.setNombre(request.getNombre());
        complemento.setTipoComplemento(request.getTipoComplemento());
        complemento.setPrecioAdicional(request.getPrecioAdicional());
        complemento.setInsumoAsociado(buscarInsumoOpcional(request.getInsumoAsociadoId()));
        if (request.getActivo() != null) {
            complemento.setActivo(request.getActivo());
        }
        complemento.setModifiedBy(UsuarioActual.username());
        return ComplementoResponseDto.fromEntity(complementoRepository.save(complemento));
    }

    @Override
    @Transactional
    public ComplementoResponseDto cambiarActivo(UUID id, boolean activo) {
        ComplementoPlatillo complemento = buscar(id);
        complemento.setActivo(activo);
        complemento.setModifiedBy(UsuarioActual.username());
        return ComplementoResponseDto.fromEntity(complementoRepository.save(complemento));
    }

    private ComplementoPlatillo buscar(UUID id) {
        return complementoRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el complemento", id));
    }

    private Insumo buscarInsumoOpcional(UUID insumoId) {
        if (insumoId == null) {
            return null;
        }
        return insumoRepository.findById(insumoId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el insumo", insumoId));
    }
}
