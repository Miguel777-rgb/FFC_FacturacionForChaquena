package com.chaquena.backend_logistica.auth.repository;

import com.chaquena.backend_logistica.auth.domain.Cargo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CargoRepository extends JpaRepository<Cargo, Integer> {

    Optional<Cargo> findByNombre(String nombre);

    boolean existsByNombre(String nombre);

    Optional<Cargo> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    /** Carga el cargo con sus roles, para armar las autoridades del JWT sin N+1. */
    @EntityGraph(attributePaths = { "cargoRoles", "cargoRoles.rol" })
    Optional<Cargo> findConRolesById(Integer id);
}
