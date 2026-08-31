package com.chaquena.backend_logistica.inventario.repository;

import com.chaquena.backend_logistica.inventario.domain.InsumoPlatillo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InsumoPlatilloRepository extends JpaRepository<InsumoPlatillo, Integer> {

    List<InsumoPlatillo> findByPlatilloId(UUID platilloId);

    void deleteByPlatilloId(UUID platilloId);

    boolean existsByInsumoId(UUID insumoId);
}
