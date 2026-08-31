package com.chaquena.backend_logistica.pedidos.repository;

import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, UUID> {

    /**
     * Hibernate no puede traer dos colecciones tipo bag en la misma consulta
     * (detalles y detalles.complementos), asi que solo se precarga detalles.
     * Los complementos se resuelven de forma diferida dentro de la misma
     * transaccion, que es donde se arman los DTO.
     */
    @EntityGraph(attributePaths = { "detalles", "detalles.platillo", "cliente", "mesa", "promocion" })
    Optional<Orden> findCompletaById(UUID id);

    @Query("""
            select o from Orden o
            where (:estado is null or o.estado = :estado)
              and (:canal is null or o.canalOrigen = :canal)
              and (:tipoOrden is null or o.tipoOrden = :tipoOrden)
              and (:clienteId is null or o.cliente.id = :clienteId)
              and (:mesaNumero is null or o.mesaNumero = :mesaNumero)
              and (cast(:desde as timestamp) is null or o.dateCreated >= :desde)
              and (cast(:hasta as timestamp) is null or o.dateCreated <= :hasta)
            """)
    Page<Orden> buscar(@Param("estado") EstadoOrdenEnum estado,
            @Param("canal") com.chaquena.backend_logistica.pedidos.domain.CanalOrigenEnum canal,
            @Param("tipoOrden") com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum tipoOrden,
            @Param("clienteId") UUID clienteId,
            @Param("mesaNumero") String mesaNumero,
            @Param("desde") ZonedDateTime desde,
            @Param("hasta") ZonedDateTime hasta,
            Pageable pageable);

    @EntityGraph(attributePaths = { "cliente", "mesa", "detalles" })
    List<Orden> findByEstadoInOrderByDateCreatedAsc(List<EstadoOrdenEnum> estados);

    List<Orden> findByClienteIdOrderByDateCreatedDesc(UUID clienteId);

    long countByClienteIdAndEstado(UUID clienteId, EstadoOrdenEnum estado);

    @Query("""
            select coalesce(sum(o.montoTotal), 0) from Orden o
            where o.estado in :estados and o.dateCreated between :desde and :hasta
            """)
    java.math.BigDecimal totalVendido(@Param("estados") List<EstadoOrdenEnum> estados,
            @Param("desde") ZonedDateTime desde, @Param("hasta") ZonedDateTime hasta);

    /**
     * Desglose del mismo total por canal de entrada.
     *
     * <p>Filtra por los mismos estados que {@link #totalVendido}: una comanda
     * todavia en cocina no es una venta. Antes excluia solo las canceladas y las
     * fraudulentas, de modo que sumaba las abiertas y el desglose no cuadraba
     * con el total que encabeza el mismo reporte.
     */
    @Query("""
            select o.canalOrigen, count(o), coalesce(sum(o.montoTotal), 0)
            from Orden o
            where o.estado in :estados and o.dateCreated between :desde and :hasta
            group by o.canalOrigen
            """)
    List<Object[]> ventasPorCanal(@Param("desde") ZonedDateTime desde, @Param("hasta") ZonedDateTime hasta,
            @Param("estados") List<EstadoOrdenEnum> estados);
}
