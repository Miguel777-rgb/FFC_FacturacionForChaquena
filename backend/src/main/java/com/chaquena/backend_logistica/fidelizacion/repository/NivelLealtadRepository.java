package com.chaquena.backend_logistica.fidelizacion.repository;

import com.chaquena.backend_logistica.fidelizacion.domain.NivelLealtad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NivelLealtadRepository extends JpaRepository<NivelLealtad, UUID> {

    List<NivelLealtad> findAllByOrderByPuntosMinimosAsc();

    Optional<NivelLealtad> findByNombreIgnoreCase(String nombre);

    Optional<NivelLealtad> findByPuntosMinimos(Integer puntosMinimos);
}
