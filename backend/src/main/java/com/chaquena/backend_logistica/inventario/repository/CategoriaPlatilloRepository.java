package com.chaquena.backend_logistica.inventario.repository;

import com.chaquena.backend_logistica.inventario.domain.CategoriaPlatillo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoriaPlatilloRepository extends JpaRepository<CategoriaPlatillo, Integer> {
    Optional<CategoriaPlatillo> findByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCase(String nombre);
}
