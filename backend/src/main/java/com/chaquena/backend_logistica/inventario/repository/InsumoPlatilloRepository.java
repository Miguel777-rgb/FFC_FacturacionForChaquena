package com.chaquena.backend_logistica.inventario.repository;

import com.chaquena.backend_logistica.inventario.domain.InsumoPlatillo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface InsumoPlatilloRepository extends JpaRepository<InsumoPlatillo, Integer> {

    List<InsumoPlatillo> findByPlatilloId(UUID platilloId);

    /** Las recetas de una pagina de platillos en una consulta, con el insumo ya cargado. */
    @EntityGraph(attributePaths = "insumo")
    List<InsumoPlatillo> findByPlatilloIdIn(Collection<UUID> platilloIds);

    void deleteByPlatilloId(UUID platilloId);

    boolean existsByInsumoId(UUID insumoId);
}
