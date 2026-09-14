package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.inventario.domain.Insumo;
import com.chaquena.backend_logistica.inventario.domain.LoteInsumo;
import com.chaquena.backend_logistica.inventario.dto.InsumoResponseDto;
import com.chaquena.backend_logistica.inventario.repository.LoteInsumoRepository;
import com.chaquena.backend_logistica.inventario.service.ReglaLotes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lee los lotes de muchos insumos en una sola consulta y los resume por
 * insumo. Una lista de trescientos insumos no puede costar trescientas
 * consultas solo para decir cual vence manana.
 */
@Component
@RequiredArgsConstructor
public class ResumenLotes {

    private final LoteInsumoRepository loteRepository;

    public Map<UUID, ReglaLotes.Resumen> de(Collection<UUID> insumoIds) {
        if (insumoIds.isEmpty()) return Map.of();

        Map<UUID, List<LoteInsumo>> porInsumo = loteRepository
                .findByInsumoIdInAndCantidadRestanteGreaterThan(insumoIds, BigDecimal.ZERO).stream()
                .collect(Collectors.groupingBy(l -> l.getInsumo().getId()));

        var hoy = ReglaLotes.hoy();
        Map<UUID, ReglaLotes.Resumen> resumen = new HashMap<>();
        porInsumo.forEach((id, lotes) -> resumen.put(id, ReglaLotes.resumir(lotes, hoy)));
        return resumen;
    }

    /** Los insumos convertidos a respuesta, cada uno con lo que dicen sus lotes. */
    public List<InsumoResponseDto> conLotes(List<Insumo> insumos) {
        Map<UUID, ReglaLotes.Resumen> resumen = de(insumos.stream().map(Insumo::getId).toList());
        return insumos.stream()
                .map(i -> InsumoResponseDto.fromEntity(i, resumen.get(i.getId())))
                .toList();
    }
}
