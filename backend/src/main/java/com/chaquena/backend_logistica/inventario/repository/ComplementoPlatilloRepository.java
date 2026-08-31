package com.chaquena.backend_logistica.inventario.repository;

import com.chaquena.backend_logistica.inventario.domain.ComplementoPlatillo;
import com.chaquena.backend_logistica.inventario.domain.TipoComplementoEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComplementoPlatilloRepository extends JpaRepository<ComplementoPlatillo, UUID> {

    List<ComplementoPlatillo> findByActivoTrue();

    List<ComplementoPlatillo> findByTipoComplementoAndActivoTrue(TipoComplementoEnum tipoComplemento);
}
