package com.chaquena.backend_logistica.inventario.repository;

import com.chaquena.backend_logistica.inventario.domain.Insumo;
import com.chaquena.backend_logistica.inventario.domain.TipoInsumoEnum;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsumoRepository extends JpaRepository<Insumo, UUID> {

    /** El termino llega normalizado como patron en minusculas; ver PlatilloRepository. */
    @Query("""
            select i from Insumo i
            where (:tipo is null or i.tipoInsumo = :tipo)
              and lower(i.nombre) like :termino
              and (:bajoMinimo = false or i.stockActual <= i.stockMinimo)
            """)
    Page<Insumo> buscar(@Param("tipo") TipoInsumoEnum tipo,
            @Param("termino") String termino,
            @Param("bajoMinimo") boolean bajoMinimo,
            Pageable pageable);

    @Query("select i from Insumo i where i.stockActual <= i.stockMinimo order by i.nombre")
    List<Insumo> bajoMinimo();

    /**
     * Bloqueo pesimista sobre la fila del insumo. Sin esto, dos comandas
     * simultaneas que usan el mismo insumo leen el mismo stock_actual y una
     * de las dos escritura se pierde, descuadrando el inventario.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Insumo i where i.id = :id")
    Optional<Insumo> findByIdParaActualizar(@Param("id") UUID id);

    long countByStockActualLessThanEqual(java.math.BigDecimal umbral);
}
