package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.inventario.domain.Insumo;
import com.chaquena.backend_logistica.inventario.domain.TipoInsumoEnum;
import com.chaquena.backend_logistica.inventario.dto.InsumoRequestDto;
import com.chaquena.backend_logistica.inventario.dto.InsumoResponseDto;
import com.chaquena.backend_logistica.inventario.repository.InsumoRepository;
import com.chaquena.backend_logistica.inventario.repository.LoteInsumoRepository;
import com.chaquena.backend_logistica.inventario.service.InsumoService;
import com.chaquena.backend_logistica.inventario.service.ReglaLotes;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InsumoServiceImpl implements InsumoService {

    private final InsumoRepository insumoRepository;
    private final LoteInsumoRepository loteRepository;
    private final ResumenLotes resumenLotes;

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

    /** Los lotes de toda la pagina se leen de una vez, no uno por insumo. */
    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<InsumoResponseDto> buscar(TipoInsumoEnum tipo, String termino, boolean bajoMinimo,
            Pageable pageable) {
        String t = (termino == null || termino.isBlank())
                ? "%"
                : "%" + termino.trim().toLowerCase() + "%";
        Page<Insumo> pagina = insumoRepository.buscar(tipo, t, bajoMinimo, pageable);
        PageResponseDto<InsumoResponseDto> respuesta = PageResponseDto.de(pagina, InsumoResponseDto::fromEntity);
        respuesta.setContenido(resumenLotes.conLotes(pagina.getContent()));
        return respuesta;
    }

    @Override
    @Transactional(readOnly = true)
    public InsumoResponseDto obtenerPorId(UUID id) {
        return resumenLotes.conLotes(List.of(buscar(id))).getFirst();
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
        return resumenLotes.conLotes(List.of(insumoRepository.save(insumo))).getFirst();
    }

    /**
     * Lo que pide atencion en el almacen: lo que esta en o bajo su minimo, y lo
     * que tiene algo vencido o por vencer. Cada insumo dice cual de las dos cosas
     * le pasa, para que quien solo mire el minimo pueda filtrar.
     */
    @Override
    @Transactional(readOnly = true)
    public List<InsumoResponseDto> alertas() {
        Map<UUID, Insumo> porId = new LinkedHashMap<>();
        insumoRepository.bajoMinimo().forEach(i -> porId.put(i.getId(), i));

        var limite = ReglaLotes.hoy().plusDays(ReglaLotes.DIAS_AVISO_VENCIMIENTO);
        insumoRepository.findAllById(loteRepository.insumosConVencimientoHasta(limite))
                .forEach(i -> porId.putIfAbsent(i.getId(), i));

        List<Insumo> lista = new ArrayList<>(porId.values());
        lista.sort(Comparator.comparing(Insumo::getNombre, String.CASE_INSENSITIVE_ORDER));
        return resumenLotes.conLotes(lista);
    }

    private Insumo buscar(UUID id) {
        return insumoRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el insumo", id));
    }
}
