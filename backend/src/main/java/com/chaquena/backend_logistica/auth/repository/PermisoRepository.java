package com.chaquena.backend_logistica.auth.repository;

import com.chaquena.backend_logistica.auth.domain.Permiso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermisoRepository extends JpaRepository<Permiso, Integer> {
    Optional<Permiso> findByNombre(String nombre);
    boolean existsByNombre(String nombre);
}
