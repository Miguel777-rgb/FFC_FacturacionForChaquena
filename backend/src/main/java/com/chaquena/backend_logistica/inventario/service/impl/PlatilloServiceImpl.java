package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.inventario.domain.*;
import com.chaquena.backend_logistica.inventario.dto.*;
import com.chaquena.backend_logistica.inventario.repository.*;
import com.chaquena.backend_logistica.inventario.service.PlatilloService;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatilloServiceImpl implements PlatilloService {

    private final PlatilloRepository platilloRepository;
    private final CategoriaPlatilloRepository categoriaRepository;
    private final InsumoRepository insumoRepository;

    @Override
    @Transactional
    public PlatilloResponseDto crear(PlatilloRequestDto request) {
        Platillo platillo = Platillo.builder()
                .categoria(buscarCategoria(request.getCategoriaId()))
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .precioVentaBase(request.getPrecioVentaBase())
                .activo(request.getActivo() == null || request.getActivo())
                .createdBy(UsuarioActual.username())
                .build();
        return PlatilloResponseDto.fromEntity(platilloRepository.save(platillo));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<PlatilloResponseDto> buscar(Integer categoriaId, Boolean activo, String termino,
            Pageable pageable) {
        String t = patron(termino);
        return PageResponseDto.de(platilloRepository.buscar(categoriaId, activo, t, pageable),
                PlatilloResponseDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public PlatilloResponseDto obtenerPorId(UUID id) {
        return PlatilloResponseDto.conReceta(buscarConReceta(id));
    }

    @Override
    @Transactional
    public PlatilloResponseDto actualizar(UUID id, PlatilloRequestDto request) {
        Platillo platillo = buscar(id);
        platillo.setCategoria(buscarCategoria(request.getCategoriaId()));
        platillo.setNombre(request.getNombre());
        platillo.setDescripcion(request.getDescripcion());
        platillo.setPrecioVentaBase(request.getPrecioVentaBase());
        if (request.getActivo() != null) {
            platillo.setActivo(request.getActivo());
        }
        platillo.setModifiedBy(UsuarioActual.username());
        return PlatilloResponseDto.fromEntity(platilloRepository.save(platillo));
    }

    @Override
    @Transactional
    public PlatilloResponseDto cambiarActivo(UUID id, boolean activo) {
        Platillo platillo = buscar(id);
        platillo.setActivo(activo);
        platillo.setModifiedBy(UsuarioActual.username());
        return PlatilloResponseDto.fromEntity(platilloRepository.save(platillo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecetaItemDto> obtenerReceta(UUID id) {
        Platillo platillo = buscarConReceta(id);
        return platillo.getReceta().stream().map(RecetaItemDto::fromEntity).toList();
    }

    /**
     * Reemplaza la receta completa en una sola transaccion. Se hace por
     * sustitucion y no por diferencias para que el BOM del platillo sea
     * siempre exactamente lo que envio el administrador.
     */
    @Override
    @Transactional
    public List<RecetaItemDto> reemplazarReceta(UUID id, RecetaRequestDto request) {
        Platillo platillo = buscarConReceta(id);
        platillo.getReceta().clear();

        for (RecetaItemDto item : request.getInsumos()) {
            Insumo insumo = insumoRepository.findById(item.getInsumoId())
                    .orElseThrow(() -> RecursoNoEncontradoException.de("el insumo", item.getInsumoId()));
            InsumoPlatillo linea = InsumoPlatillo.builder()
                    .platillo(platillo)
                    .insumo(insumo)
                    .cantidadRequerida(item.getCantidadRequerida())
                    .createdBy(UsuarioActual.username())
                    .build();
            platillo.getReceta().add(linea);
        }

        platillo.setModifiedBy(UsuarioActual.username());
        Platillo guardado = platilloRepository.save(platillo);
        return guardado.getReceta().stream().map(RecetaItemDto::fromEntity).toList();
    }

    /**
     * Carta con disponibilidad resuelta: para cada platillo activo calcula
     * cuantas porciones alcanzan los insumos, tomando el insumo mas escaso de
     * la receta como limite. Un platillo sin receta se considera siempre
     * disponible (bebidas embotelladas, por ejemplo).
     */
    @Override
    @Transactional(readOnly = true)
    public List<PlatilloDisponibleDto> menuDisponible() {
        List<Platillo> platillos = platilloRepository.findByActivoTrue();
        List<PlatilloDisponibleDto> resultado = new ArrayList<>();

        for (Platillo platillo : platillos) {
            List<InsumoPlatillo> receta = platillo.getReceta();
            Integer porciones = null;
            List<String> faltantes = new ArrayList<>();

            if (receta != null && !receta.isEmpty()) {
                porciones = Integer.MAX_VALUE;
                for (InsumoPlatillo linea : receta) {
                    BigDecimal requerida = linea.getCantidadRequerida();
                    if (requerida == null || requerida.signum() <= 0) {
                        continue;
                    }
                    BigDecimal stock = linea.getInsumo().getStockActual() != null
                            ? linea.getInsumo().getStockActual()
                            : BigDecimal.ZERO;
                    int posibles = stock.divide(requerida, 0, RoundingMode.DOWN).intValue();
                    porciones = Math.min(porciones, Math.max(posibles, 0));
                    if (posibles <= 0) {
                        faltantes.add(linea.getInsumo().getNombre());
                    }
                }
                if (porciones == Integer.MAX_VALUE) {
                    porciones = null;
                }
            }

            resultado.add(PlatilloDisponibleDto.builder()
                    .id(platillo.getId())
                    .nombre(platillo.getNombre())
                    .descripcion(platillo.getDescripcion())
                    .categoriaId(platillo.getCategoria() != null ? platillo.getCategoria().getId() : null)
                    .categoriaNombre(platillo.getCategoria() != null ? platillo.getCategoria().getNombre() : null)
                    .precioVentaBase(platillo.getPrecioVentaBase())
                    .porcionesPosibles(porciones)
                    .disponible(porciones == null || porciones > 0)
                    .insumosFaltantes(faltantes)
                    .build());
        }
        return resultado;
    }

    /** Convierte el texto libre en el patron LIKE que espera el repositorio. */
    private String patron(String termino) {
        return (termino == null || termino.isBlank())
                ? "%"
                : "%" + termino.trim().toLowerCase() + "%";
    }

    private Platillo buscar(UUID id) {
        return platilloRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el platillo", id));
    }

    private Platillo buscarConReceta(UUID id) {
        return platilloRepository.findWithRecetaById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el platillo", id));
    }

    private CategoriaPlatillo buscarCategoria(Integer id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la categoria", id));
    }
}
