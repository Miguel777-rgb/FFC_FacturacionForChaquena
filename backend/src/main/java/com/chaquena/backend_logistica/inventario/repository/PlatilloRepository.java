package com.chaquena.backend_logistica.inventario.repository;

import com.chaquena.backend_logistica.inventario.domain.Platillo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatilloRepository extends JpaRepository<Platillo, UUID> {

    /**
     * El termino llega ya normalizado como patron en minusculas ("%lomo%", o
     * "%" cuando no hay filtro). Si se dejara que el parametro fuera null
     * dentro de lower(), PostgreSQL no puede inferir su tipo, lo liga como
     * bytea y la consulta falla con "function lower(bytea) does not exist".
     */
    @Query("""
            select p from Platillo p
            where (:categoriaId is null or p.categoria.id = :categoriaId)
              and (:activo is null or p.activo = :activo)
              and lower(p.nombre) like :termino
            """)
    Page<Platillo> buscar(@Param("categoriaId") Integer categoriaId,
            @Param("activo") Boolean activo,
            @Param("termino") String termino,
            Pageable pageable);

    /** Carga el platillo con su receta para no caer en N+1 al explotar el BOM. */
    @EntityGraph(attributePaths = { "receta", "receta.insumo", "categoria" })
    Optional<Platillo> findWithRecetaById(UUID id);

    @EntityGraph(attributePaths = { "receta", "receta.insumo", "categoria" })
    List<Platillo> findByActivoTrue();

    boolean existsByCategoriaId(Integer categoriaId);
}
