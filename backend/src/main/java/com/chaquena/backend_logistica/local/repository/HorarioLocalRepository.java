package com.chaquena.backend_logistica.local.repository;

import com.chaquena.backend_logistica.local.domain.HorarioLocal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface HorarioLocalRepository extends JpaRepository<HorarioLocal, UUID> {
}
