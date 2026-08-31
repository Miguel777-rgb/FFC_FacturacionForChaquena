package com.chaquena.backend_logistica.delivery.repository;

import com.chaquena.backend_logistica.delivery.domain.Transportista;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransportistaRepository extends JpaRepository<Transportista, UUID> {

    @EntityGraph(attributePaths = { "vehiculos" })
    List<Transportista> findByActivoTrue();

    @EntityGraph(attributePaths = { "vehiculos" })
    Optional<Transportista> findConVehiculosById(UUID id);

    Page<Transportista> findByEmpresaTransporteContainingIgnoreCase(String empresa, Pageable pageable);

    Optional<Transportista> findByDni(String dni);
}
