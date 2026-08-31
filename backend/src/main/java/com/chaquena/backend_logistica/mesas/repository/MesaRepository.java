package com.chaquena.backend_logistica.mesas.repository;

import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.domain.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MesaRepository extends JpaRepository<Mesa, UUID> {

    Optional<Mesa> findByNumero(String numero);

    boolean existsByNumero(String numero);

    List<Mesa> findByActivaTrueOrderByZonaAscNumeroAsc();

    List<Mesa> findByEstado(EstadoMesaEnum estado);
}
