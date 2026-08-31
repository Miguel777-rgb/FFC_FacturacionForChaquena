package com.chaquena.backend_logistica.delivery.repository;

import com.chaquena.backend_logistica.delivery.domain.SesionBot;
import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SesionBotRepository extends JpaRepository<SesionBot, UUID> {

    Optional<SesionBot> findByCanalAndRemitenteId(CanalBot canal, String remitenteId);
}
