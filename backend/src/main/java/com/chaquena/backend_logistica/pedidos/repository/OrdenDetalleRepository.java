package com.chaquena.backend_logistica.pedidos.repository;

import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.OrdenDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrdenDetalleRepository extends JpaRepository<OrdenDetalle, UUID> {

    List<OrdenDetalle> findByOrdenId(UUID ordenId);

    /** Notas de excepcion previas del cliente, para sugerirlas en la proxima comanda. */
    @Query("""
            select distinct d.excepcionesNota from OrdenDetalle d
            where d.orden.cliente.id = :clienteId
              and d.excepcionesNota is not null
              and d.excepcionesNota <> ''
            order by d.excepcionesNota
            """)
    List<String> notasHistoricasDelCliente(@Param("clienteId") UUID clienteId);

    /**
     * Ranking de platillos vendidos, con el mismo criterio de venta efectiva que
     * usa el reporte de ventas. Sin el filtro por estado, un platillo de una
     * comanda cancelada o marcada como fraudulenta subia igual en el ranking y
     * el mas vendido del mes podia ser uno que nunca salio de cocina.
     */
    @Query("""
            select d.platillo.nombre, sum(d.cantidad), coalesce(sum(d.montoSubtotal), 0)
            from OrdenDetalle d
            where d.orden.estado in :estados and d.orden.dateCreated between :desde and :hasta
            group by d.platillo.nombre
            order by sum(d.cantidad) desc
            """)
    List<Object[]> platillosMasVendidos(@Param("desde") ZonedDateTime desde,
            @Param("hasta") ZonedDateTime hasta,
            @Param("estados") List<EstadoOrdenEnum> estados);
}
