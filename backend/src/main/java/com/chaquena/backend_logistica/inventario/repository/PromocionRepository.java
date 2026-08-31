package com.chaquena.backend_logistica.inventario.repository;

import com.chaquena.backend_logistica.inventario.domain.Promocion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PromocionRepository extends JpaRepository<Promocion, UUID> {

    @Query("""
            select p from Promocion p
            where p.activa = true
              and p.fechaInicio <= :momento
              and p.fechaFin >= :momento
            """)
    List<Promocion> vigentesEn(@Param("momento") ZonedDateTime momento);
}
