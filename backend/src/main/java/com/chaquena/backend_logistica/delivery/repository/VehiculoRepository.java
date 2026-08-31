package com.chaquena.backend_logistica.delivery.repository;

import com.chaquena.backend_logistica.delivery.domain.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehiculoRepository extends JpaRepository<Vehiculo, UUID> {

    List<Vehiculo> findByTransportistaId(UUID transportistaId);

    Optional<Vehiculo> findByPlacaIgnoreCase(String placa);

    boolean existsByPlacaIgnoreCase(String placa);
}
