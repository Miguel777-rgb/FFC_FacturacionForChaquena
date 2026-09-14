package com.chaquena.backend_logistica.local.repository;

import com.chaquena.backend_logistica.local.domain.DatosLocal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DatosLocalRepository extends JpaRepository<DatosLocal, Integer> {
}
