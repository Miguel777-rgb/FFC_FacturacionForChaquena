package com.chaquena.backend_logistica.asistencia.repository;

import com.chaquena.backend_logistica.asistencia.domain.Marcacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarcacionRepository extends JpaRepository<Marcacion, UUID> {

    /** La entrada que sigue abierta. Deberia haber como mucho una. */
    Optional<Marcacion> findFirstByTrabajadorIdAndSalidaIsNullOrderByEntradaDesc(UUID trabajadorId);

    List<Marcacion> findByTrabajadorIdAndEntradaBetweenOrderByEntradaAsc(UUID trabajadorId, ZonedDateTime desde,
            ZonedDateTime hasta);

    @EntityGraph(attributePaths = "trabajador")
    List<Marcacion> findByEntradaBetweenOrderByEntradaAsc(ZonedDateTime desde, ZonedDateTime hasta);
}
