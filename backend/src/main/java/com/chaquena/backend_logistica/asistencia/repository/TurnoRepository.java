package com.chaquena.backend_logistica.asistencia.repository;

import com.chaquena.backend_logistica.asistencia.domain.Turno;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TurnoRepository extends JpaRepository<Turno, UUID> {

    /** Los turnos de todo el personal en un rango, con el trabajador ya cargado. */
    @EntityGraph(attributePaths = "trabajador")
    List<Turno> findByFechaBetweenOrderByFechaAscInicioAsc(LocalDate desde, LocalDate hasta);

    List<Turno> findByTrabajadorIdAndFechaBetweenOrderByFechaAscInicioAsc(UUID trabajadorId, LocalDate desde,
            LocalDate hasta);
}
