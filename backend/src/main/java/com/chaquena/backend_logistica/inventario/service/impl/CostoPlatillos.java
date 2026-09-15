package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.inventario.domain.InsumoPlatillo;
import com.chaquena.backend_logistica.inventario.domain.LoteInsumo;
import com.chaquena.backend_logistica.inventario.domain.Platillo;
import com.chaquena.backend_logistica.inventario.repository.InsumoPlatilloRepository;
import com.chaquena.backend_logistica.inventario.repository.LoteInsumoRepository;
import com.chaquena.backend_logistica.inventario.service.ReglaCosto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Costo y margen de una pagina de platillos con dos consultas, sean cuantos
 * sean: las recetas de todos y el ultimo costo de cada insumo que aparece en
 * ellas.
 */
@Component
@RequiredArgsConstructor
public class CostoPlatillos {

    private final InsumoPlatilloRepository recetaRepository;
    private final LoteInsumoRepository loteRepository;

    public Map<UUID, ReglaCosto.Costo> de(Collection<Platillo> platillos) {
        if (platillos.isEmpty()) {
            return Map.of();
        }

        List<UUID> ids = platillos.stream().map(Platillo::getId).toList();
        Map<UUID, List<ReglaCosto.Linea>> recetas = new HashMap<>();
        for (InsumoPlatillo linea : recetaRepository.findByPlatilloIdIn(ids)) {
            recetas.computeIfAbsent(linea.getPlatillo().getId(), k -> new ArrayList<>())
                    .add(new ReglaCosto.Linea(linea.getInsumo().getId(), linea.getInsumo().getNombre(),
                            linea.getCantidadRequerida()));
        }

        Set<UUID> insumos = new HashSet<>();
        recetas.values().forEach(lineas -> lineas.forEach(l -> insumos.add(l.insumoId())));

        Map<UUID, BigDecimal> costos = new HashMap<>();
        if (!insumos.isEmpty()) {
            for (LoteInsumo lote : loteRepository.ultimosConCosto(insumos)) {
                costos.putIfAbsent(lote.getInsumo().getId(), lote.getCostoUnitario());
            }
        }

        Map<UUID, ReglaCosto.Costo> resultado = new HashMap<>();
        for (Platillo platillo : platillos) {
            resultado.put(platillo.getId(), ReglaCosto.calcular(platillo.getPrecioVentaBase(),
                    recetas.getOrDefault(platillo.getId(), List.of()), costos));
        }
        return resultado;
    }
}
