package com.chaquena.backend_logistica.inventario.repository;

import com.chaquena.backend_logistica.inventario.domain.LoteInsumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Los lotes se escriben siempre con la fila del insumo bloqueada
 * ({@link InsumoRepository#findByIdParaActualizar}): ese bloqueo ya serializa
 * todo lo que toca el stock de un insumo, asi que los lotes no llevan uno propio.
 */
@Repository
public interface LoteInsumoRepository extends JpaRepository<LoteInsumo, UUID> {

    List<LoteInsumo> findByInsumoId(UUID insumoId);

    /** Lo que queda de un insumo; se llama con cero. */
    List<LoteInsumo> findByInsumoIdAndCantidadRestanteGreaterThan(UUID insumoId, BigDecimal minimo);

    List<LoteInsumo> findByInsumoIdInAndCantidadRestanteGreaterThan(Collection<UUID> insumoIds, BigDecimal minimo);

    /** Lotes de los que ya salio algo: donde vuelve lo que se repone al cancelar. */
    @Query("select l from LoteInsumo l where l.insumo.id = :insumoId and l.cantidadRestante < l.cantidadInicial")
    List<LoteInsumo> consumidosDe(@Param("insumoId") UUID insumoId);

    /** Insumos con algo que ya vencio o vence hasta el limite. */
    @Query("""
            select distinct l.insumo.id from LoteInsumo l
            where l.cantidadRestante > 0 and l.fechaVencimiento <= :limite
            """)
    List<UUID> insumosConVencimientoHasta(@Param("limite") LocalDate limite);

    /**
     * El lote mas reciente con costo de cada insumo: lo que costo la ultima
     * compra, que es lo que costaria reponerlo. Los lotes iniciales no tienen
     * costo y no cuentan.
     */
    @Query("""
            select l from LoteInsumo l
            where l.insumo.id in :insumoIds and l.costoUnitario is not null
              and l.dateCreated = (select max(o.dateCreated) from LoteInsumo o
                                   where o.insumo = l.insumo and o.costoUnitario is not null)
            """)
    List<LoteInsumo> ultimosConCosto(@Param("insumoIds") Collection<UUID> insumoIds);
}
