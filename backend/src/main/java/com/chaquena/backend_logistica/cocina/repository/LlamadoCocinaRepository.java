package com.chaquena.backend_logistica.cocina.repository;

import com.chaquena.backend_logistica.cocina.domain.EstadoLlamadoEnum;
import com.chaquena.backend_logistica.cocina.domain.LlamadoCocina;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LlamadoCocinaRepository extends JpaRepository<LlamadoCocina, UUID> {

    Optional<LlamadoCocina> findFirstByOrdenIdAndEstado(UUID ordenId, EstadoLlamadoEnum estado);

    List<LlamadoCocina> findByOrdenIdAndEstado(UUID ordenId, EstadoLlamadoEnum estado);

    /**
     * El primer «Voy» gana, y lo decide la base de datos: la condicion sobre el
     * estado hace que de dos mozos que pulsan a la vez solo uno cambie la fila.
     * Devuelve 1 si este mozo se lo llevo y 0 si llego tarde.
     *
     * <p>Una consulta de actualizacion no pasa por {@code @PreUpdate}, asi que
     * la auditoria se escribe aqui.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LlamadoCocina l
               set l.estado = :atendido,
                   l.atendidoPor = :por,
                   l.atendidoPorNombre = :nombre,
                   l.atendidoEn = :en,
                   l.modifiedBy = :por,
                   l.lastDateModified = :en
             where l.id = :id and l.estado = :pendiente""")
    int atender(@Param("id") UUID id, @Param("por") String por, @Param("nombre") String nombre,
            @Param("en") ZonedDateTime en,
            @Param("pendiente") EstadoLlamadoEnum pendiente, @Param("atendido") EstadoLlamadoEnum atendido);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LlamadoCocina l
               set l.estado = :cerrado,
                   l.cerradoEn = :en,
                   l.modifiedBy = :por,
                   l.lastDateModified = :en
             where l.orden.id = :ordenId and l.estado = :pendiente""")
    int cerrarPendientesDe(@Param("ordenId") UUID ordenId, @Param("en") ZonedDateTime en,
            @Param("por") String por,
            @Param("pendiente") EstadoLlamadoEnum pendiente, @Param("cerrado") EstadoLlamadoEnum cerrado);

    /** Los llamados vivos de las comandas que siguen en el pase. */
    @Query("""
            select l from LlamadoCocina l join fetch l.orden o
             where o.estado = :enPreparacion and l.estado <> :cerrado
             order by l.llamadoEn""")
    List<LlamadoCocina> deComandasEnElPase(@Param("enPreparacion") EstadoOrdenEnum enPreparacion,
            @Param("cerrado") EstadoLlamadoEnum cerrado);
}
