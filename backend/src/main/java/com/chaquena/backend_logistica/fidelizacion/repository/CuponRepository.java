package com.chaquena.backend_logistica.fidelizacion.repository;

import com.chaquena.backend_logistica.fidelizacion.domain.Cupon;
import com.chaquena.backend_logistica.fidelizacion.domain.EstadoCuponEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CuponRepository extends JpaRepository<Cupon, UUID> {

    Optional<Cupon> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    List<Cupon> findByClienteIdOrderByFechaEmisionDesc(UUID clienteId);

    List<Cupon> findByClienteIdAndEstado(UUID clienteId, EstadoCuponEnum estado);
}
