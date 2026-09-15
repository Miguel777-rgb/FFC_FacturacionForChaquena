package com.chaquena.backend_logistica.inventario.repository;

import com.chaquena.backend_logistica.inventario.domain.Alergeno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlergenoRepository extends JpaRepository<Alergeno, Integer> {

    List<Alergeno> findAllByOrderByNombreAsc();

    List<Alergeno> findByActivoTrueOrderByNombreAsc();

    Optional<Alergeno> findByNombreIgnoreCase(String nombre);
}
