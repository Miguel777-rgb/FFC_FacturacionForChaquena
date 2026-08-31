package com.chaquena.backend_logistica.delivery.repository;

import com.chaquena.backend_logistica.delivery.domain.OrdenDeliveryInfo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrdenDeliveryInfoRepository extends JpaRepository<OrdenDeliveryInfo, Long> {

    Optional<OrdenDeliveryInfo> findByOrdenId(UUID ordenId);

    /** Tablero de reparto: lo que salio y todavia no fue entregado. */
    @EntityGraph(attributePaths = { "orden", "transportista" })
    List<OrdenDeliveryInfo> findByHoraDespachoNotNullAndHoraEntregaIsNull();

    List<OrdenDeliveryInfo> findByTransportistaId(UUID transportistaId);
}
