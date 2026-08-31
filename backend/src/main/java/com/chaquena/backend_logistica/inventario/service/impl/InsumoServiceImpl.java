package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.inventario.domain.Insumo;
import com.chaquena.backend_logistica.inventario.domain.TipoInsumoEnum;
import com.chaquena.backend_logistica.inventario.dto.InsumoRequestDto;
import com.chaquena.backend_logistica.inventario.dto.InsumoResponseDto;
import com.chaquena.backend_logistica.inventario.repository.InsumoRepository;
import com.chaquena.backend_logistica.inventario.service.InsumoService;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InsumoServiceImpl implements InsumoService {

    private final InsumoRepository insumoRepository;

    @Override
    @Transactional
    public InsumoResponseDto crear(InsumoRequestDto request) {
        Insumo insumo = Insumo.builder()
                .nombre(request.getNombre())
                .tipoInsumo(request.getTipoInsumo())
                .unidadMedida(request.getUnidadMedida())
                .stockActual(BigDecimal.ZERO)
                .stockMinimo(request.getStockMinimo() != null ? request.getStockMinimo() : BigDecimal.ZERO)
                .createdBy(UsuarioActual.username())
                .build();
        return InsumoResponseDto.fromEntity(insumoRepository.save(insumo));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<InsumoResponseDto> buscar(TipoInsumoEnum tipo, String termino, boolean bajoMinimo,
            Pageable pageable) {
        String t = (termino == null || termino.isBlank())
                ? "%"
                : "%" + termino.trim().toLowerCase() + "%";
        return PageResponseDto.de(insumoRepository.buscar(tipo, t, bajoMinimo, pageable),
                InsumoResponseDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public InsumoResponseDto obtenerPorId(UUID id) {
        return InsumoResponseDto.fromEntity(buscar(id));
    }

    /**
     * Edita solo los datos maestros. El stock nunca se toca por aqui: se mueve
     * exclusivamente con un movimiento en el kardex, para que siempre exista el
     * rastro de quien lo cambio y por que.
     */
    @Override
    @Transactional
    public InsumoResponseDto actualizar(UUID id, InsumoRequestDto request) {
        Insumo insumo = buscar(id);
        insumo.setNombre(request.getNombre());
        insumo.setTipoInsumo(request.getTipoInsumo());
        insumo.setUnidadMedida(request.getUnidadMedida());
        if (request.getStockMinimo() != null) {
            insumo.setStockMinimo(request.getStockMinimo());
        }
        insumo.setModifiedBy(UsuarioActual.username());
        return InsumoResponseDto.fromEntity(insumoRepository.save(insumo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InsumoResponseDto> alertas() {
        return insumoRepository.bajoMinimo().stream().map(InsumoResponseDto::fromEntity).toList();
    }

    private Insumo buscar(UUID id) {
        return insumoRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el insumo", id));
    }
}
