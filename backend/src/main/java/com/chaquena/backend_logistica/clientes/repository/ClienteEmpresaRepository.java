package com.chaquena.backend_logistica.clientes.repository;

import com.chaquena.backend_logistica.clientes.domain.ClienteEmpresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClienteEmpresaRepository extends JpaRepository<ClienteEmpresa, Integer> {

    List<ClienteEmpresa> findByPersonaId(UUID personaId);

    List<ClienteEmpresa> findByEmpresaId(UUID empresaId);

    boolean existsByPersonaIdAndEmpresaId(UUID personaId, UUID empresaId);
}
