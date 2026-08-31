package com.chaquena.backend_logistica.pedidos.repository;

import com.chaquena.backend_logistica.pedidos.domain.CalificacionFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalificacionFeedbackRepository extends JpaRepository<CalificacionFeedback, Long> {

    Optional<CalificacionFeedback> findByOrdenId(UUID ordenId);

    boolean existsByOrdenId(UUID ordenId);

    /** Cuantas calificaciones lleva el cliente: es el contador de la regla de las N. */
    long countByClienteId(UUID clienteId);

    @Query("""
            select coalesce(avg(cast(f.puntajeAtencion as double)), 0),
                   coalesce(avg(cast(f.puntajeComida as double)), 0),
                   coalesce(avg(cast(f.puntajeLugar as double)), 0),
                   count(f)
            from CalificacionFeedback f
            where f.dateCreated between :desde and :hasta
            """)
    Object[] promedios(@Param("desde") ZonedDateTime desde, @Param("hasta") ZonedDateTime hasta);
}
