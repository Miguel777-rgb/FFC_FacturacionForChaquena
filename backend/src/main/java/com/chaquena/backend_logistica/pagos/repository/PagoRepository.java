package com.chaquena.backend_logistica.pagos.repository;

import com.chaquena.backend_logistica.pagos.domain.EstadoPagoEnum;
import com.chaquena.backend_logistica.pagos.domain.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PagoRepository extends JpaRepository<Pago, UUID> {

    List<Pago> findByOrdenIdOrderByDateCreatedAsc(UUID ordenId);

    List<Pago> findByEsFraudulentoTrueOrderByDateCreatedDesc();

    @Query("""
            select coalesce(sum(p.monto), 0) from Pago p
            where p.orden.id = :ordenId and p.estado = :estado
            """)
    BigDecimal totalConfirmadoDeOrden(@Param("ordenId") UUID ordenId,
            @Param("estado") EstadoPagoEnum estado);

    /** Arqueo de caja: cuanto entro por cada metodo en el rango de fechas. */
    @Query("""
            select p.tipoPago, count(p), coalesce(sum(p.monto), 0)
            from Pago p
            where p.estado = :estado and p.dateCreated between :desde and :hasta
            group by p.tipoPago
            """)
    List<Object[]> arqueoPorMetodo(@Param("estado") EstadoPagoEnum estado,
            @Param("desde") ZonedDateTime desde, @Param("hasta") ZonedDateTime hasta);
}
