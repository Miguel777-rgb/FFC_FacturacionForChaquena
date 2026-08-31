package com.chaquena.backend_logistica.pedidos.repository;

import com.chaquena.backend_logistica.pedidos.domain.OrdenDetalleComplemento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrdenDetalleComplementoRepository extends JpaRepository<OrdenDetalleComplemento, Long> {
    List<OrdenDetalleComplemento> findByOrdenDetalleId(UUID ordenDetalleId);
}
