package com.chaquena.backend_logistica.fidelizacion.repository;

import com.chaquena.backend_logistica.fidelizacion.domain.ConfiguracionLocal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionLocalRepository extends JpaRepository<ConfiguracionLocal, Integer> {
}
