package com.chaquena.backend_logistica.clientes.repository;

import com.chaquena.backend_logistica.clientes.domain.Empresa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {

    Optional<Empresa> findByRuc(String ruc);

    boolean existsByRuc(String ruc);

    Page<Empresa> findByRazonSocialContainingIgnoreCase(String razonSocial, Pageable pageable);
}
