package com.chaquena.backend_logistica.auth.repository;

import com.chaquena.backend_logistica.auth.domain.CargoRol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CargoRolRepository extends JpaRepository<CargoRol, Integer> {
}