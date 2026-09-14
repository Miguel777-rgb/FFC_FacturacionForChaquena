package com.chaquena.backend_logistica.inventario.repository;

import com.chaquena.backend_logistica.inventario.domain.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, UUID> {

    List<Proveedor> findAllByOrderByNombreAsc();

    List<Proveedor> findByActivoTrueOrderByNombreAsc();

    Optional<Proveedor> findByRuc(String ruc);

    Optional<Proveedor> findByNombreIgnoreCase(String nombre);
}
