package com.chaquena.backend_logistica.auth.repository;

import com.chaquena.backend_logistica.auth.domain.TokenRecuperacion;
import com.chaquena.backend_logistica.auth.domain.Trabajador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, UUID> {

    Optional<TokenRecuperacion> findByHuella(String huella);

    /** Cuantos se pidieron desde un instante: es el freno contra el envio en cadena. */
    long countByTrabajadorAndDateCreatedAfter(Trabajador trabajador, Instant desde);

    /**
     * Gasta los enlaces anteriores de esa persona. Pedir uno nuevo invalida el
     * viejo: si no, un enlace filtrado de hace media hora seguiria abriendo la
     * puerta aunque ya se hubiera usado otro.
     */
    @Modifying
    @Query("update TokenRecuperacion t set t.usadoEn = :ahora, t.lastDateModified = :ahora "
            + "where t.trabajador = :trabajador and t.usadoEn is null")
    int anularLosDe(@Param("trabajador") Trabajador trabajador, @Param("ahora") Instant ahora);
}
