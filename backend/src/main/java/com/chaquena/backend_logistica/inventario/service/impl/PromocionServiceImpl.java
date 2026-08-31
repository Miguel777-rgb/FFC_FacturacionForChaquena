package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.inventario.domain.Insumo;
import com.chaquena.backend_logistica.inventario.domain.Promocion;
import com.chaquena.backend_logistica.inventario.dto.PromocionRequestDto;
import com.chaquena.backend_logistica.inventario.dto.PromocionResponseDto;
import com.chaquena.backend_logistica.inventario.repository.InsumoRepository;
import com.chaquena.backend_logistica.inventario.repository.PromocionRepository;
import com.chaquena.backend_logistica.inventario.service.PromocionService;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromocionServiceImpl implements PromocionService {

    private final PromocionRepository promocionRepository;
    private final InsumoRepository insumoRepository;

    @Override
    @Transactional
    public PromocionResponseDto crear(PromocionRequestDto request) {
        validarFechas(request);
        Promocion promocion = Promocion.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .porcentajeDescuento(valorODefecto(request.getPorcentajeDescuento()))
                .montoDescuento(valorODefecto(request.getMontoDescuento()))
                .requiereInsumoExtra(Boolean.TRUE.equals(request.getRequiereInsumoExtra()))
                .insumoExtra(buscarInsumoOpcional(request.getInsumoExtraId()))
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .activa(request.getActiva() == null || request.getActiva())
                .createdBy(UsuarioActual.username())
                .build();
        return PromocionResponseDto.fromEntity(promocionRepository.save(promocion));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromocionResponseDto> listar(boolean soloVigentes) {
        List<Promocion> promociones = soloVigentes
                ? promocionRepository.vigentesEn(ZonedDateTime.now())
                : promocionRepository.findAll();
        return promociones.stream().map(PromocionResponseDto::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PromocionResponseDto obtenerPorId(UUID id) {
        return PromocionResponseDto.fromEntity(buscar(id));
    }

    @Override
    @Transactional
    public PromocionResponseDto actualizar(UUID id, PromocionRequestDto request) {
        validarFechas(request);
        Promocion promocion = buscar(id);
        promocion.setNombre(request.getNombre());
        promocion.setDescripcion(request.getDescripcion());
        promocion.setPorcentajeDescuento(valorODefecto(request.getPorcentajeDescuento()));
        promocion.setMontoDescuento(valorODefecto(request.getMontoDescuento()));
        promocion.setRequiereInsumoExtra(Boolean.TRUE.equals(request.getRequiereInsumoExtra()));
        promocion.setInsumoExtra(buscarInsumoOpcional(request.getInsumoExtraId()));
        promocion.setFechaInicio(request.getFechaInicio());
        promocion.setFechaFin(request.getFechaFin());
        if (request.getActiva() != null) {
            promocion.setActiva(request.getActiva());
        }
        promocion.setModifiedBy(UsuarioActual.username());
        return PromocionResponseDto.fromEntity(promocionRepository.save(promocion));
    }

    @Override
    @Transactional
    public PromocionResponseDto cambiarActiva(UUID id, boolean activa) {
        Promocion promocion = buscar(id);
        promocion.setActiva(activa);
        promocion.setModifiedBy(UsuarioActual.username());
        return PromocionResponseDto.fromEntity(promocionRepository.save(promocion));
    }

    /**
     * Una promocion vigente que regala un insumo extra deja de ser aplicable
     * cuando ese insumo se agota: el diseno la marca como Agotada en lugar de
     * ocultarla, para que el mozo pueda explicarle al cliente por que no esta.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PromocionResponseDto> aplicables() {
        return promocionRepository.vigentesEn(ZonedDateTime.now()).stream()
                .map(promocion -> {
                    PromocionResponseDto dto = PromocionResponseDto.fromEntity(promocion);
                    if (Boolean.TRUE.equals(promocion.getRequiereInsumoExtra())) {
                        Insumo extra = promocion.getInsumoExtra();
                        boolean hayStock = extra != null
                                && extra.getStockActual() != null
                                && extra.getStockActual().signum() > 0;
                        dto.setAplicable(hayStock);
                        if (!hayStock) {
                            dto.setMotivoNoAplicable(extra == null
                                    ? "La promocion exige un insumo extra que no esta configurado."
                                    : "Agotado: no queda stock de " + extra.getNombre() + ".");
                        }
                    } else {
                        dto.setAplicable(true);
                    }
                    return dto;
                })
                .toList();
    }

    private void validarFechas(PromocionRequestDto request) {
        if (request.getFechaInicio() != null && request.getFechaFin() != null
                && request.getFechaFin().isBefore(request.getFechaInicio())) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio.");
        }
    }

    private BigDecimal valorODefecto(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private Promocion buscar(UUID id) {
        return promocionRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la promocion", id));
    }

    private Insumo buscarInsumoOpcional(UUID insumoId) {
        if (insumoId == null) {
            return null;
        }
        return insumoRepository.findById(insumoId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el insumo", insumoId));
    }
}
