package com.chaquena.backend_logistica.auth.repository;

import com.chaquena.backend_logistica.auth.domain.RolPermiso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolPermisoRepository extends JpaRepository<RolPermiso, Integer> {

    /**
     * Permisos finos de un trabajador, atravesando cargo -> cargo_roles ->
     * rol_permisos -> permisos. Es lo que alimenta las autoridades del JWT.
     */
    @Query("""
            select distinct rp.permiso.nombre
            from RolPermiso rp
            join CargoRol cr on cr.rol.id = rp.rol.id
            where cr.cargo.id = :cargoId
            """)
    List<String> nombresPermisosPorCargo(@Param("cargoId") Integer cargoId);
}
