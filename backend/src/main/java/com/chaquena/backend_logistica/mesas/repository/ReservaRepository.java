package com.chaquena.backend_logistica.mesas.repository;

import com.chaquena.backend_logistica.mesas.domain.EstadoReservaEnum;
import com.chaquena.backend_logistica.mesas.domain.Reserva;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    /** La agenda de un dia, por hora, con la mesa ya cargada. */
    @EntityGraph(attributePaths = "mesa")
    List<Reserva> findByInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(ZonedDateTime desde,
            ZonedDateTime hasta);

    /** Las activas de una mesa que empiezan en una ventana: con las que puede chocar una nueva. */
    List<Reserva> findByMesaIdAndEstadoInAndInicioBetween(UUID mesaId, Collection<EstadoReservaEnum> estados,
            ZonedDateTime desde, ZonedDateTime hasta);

    /** Las activas de todas las mesas en una ventana, para pintar el salon con una sola consulta. */
    @EntityGraph(attributePaths = "mesa")
    List<Reserva> findByEstadoInAndInicioBetween(Collection<EstadoReservaEnum> estados, ZonedDateTime desde,
            ZonedDateTime hasta);
}
