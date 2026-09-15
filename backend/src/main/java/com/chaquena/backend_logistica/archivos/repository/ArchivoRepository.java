package com.chaquena.backend_logistica.archivos.repository;

import com.chaquena.backend_logistica.archivos.domain.Archivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ArchivoRepository extends JpaRepository<Archivo, UUID> {
}
