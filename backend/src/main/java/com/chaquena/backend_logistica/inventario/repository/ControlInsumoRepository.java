package com.chaquena.backend_logistica.inventario.repository;

import com.chaquena.backend_logistica.inventario.domain.ControlInsumo;
import com.chaquena.backend_logistica.inventario.domain.TipoControlInsumoEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ControlInsumoRepository extends JpaRepository<ControlInsumo, UUID> {

    /** Kardex de un insumo: todos sus movimientos, del mas reciente al mas viejo. */
    Page<ControlInsumo> findByInsumoIdOrderByDateCreatedDesc(UUID insumoId, Pageable pageable);

    List<ControlInsumo> findByTipoControlAndDateCreatedBetween(TipoControlInsumoEnum tipoControl,
            ZonedDateTime desde, ZonedDateTime hasta);

    @Query("""
            select c.tipoControl, count(c), coalesce(sum(abs(c.cantidad)), 0)
            from ControlInsumo c
            where c.dateCreated between :desde and :hasta
            group by c.tipoControl
            """)
    List<Object[]> resumenPorTipo(@Param("desde") ZonedDateTime desde, @Param("hasta") ZonedDateTime hasta);
}
