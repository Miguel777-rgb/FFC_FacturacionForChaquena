package com.chaquena.backend_logistica.auth.repository;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrabajadorRepository extends JpaRepository<Trabajador, UUID> {

    Optional<Trabajador> findByUsername(String username);

    Optional<Trabajador> findByCorreo(String correo);

    Optional<Trabajador> findByCorreoIgnoreCase(String correo);

    /**
     * Trabajador que ya ato su cuenta de Discord al correo con el que esta dado
     * de alta. Es la puerta de entrada del bot interno: sin esta fila, quien
     * escriba al bot es un desconocido por mucho que este en el servidor.
     *
     * <p>Trae el cargo cargado porque quien lo usa son los bots, que leen el
     * cargo fuera de la transaccion —para decidir si es administrador o para
     * mostrarlo— y con carga diferida se encontrarian la sesion ya cerrada.
     */
    @EntityGraph(attributePaths = { "cargo" })
    Optional<Trabajador> findByDiscordUserId(String discordUserId);

    Optional<Trabajador> findByUsernameOrCorreo(String username, String correo);

    // Consulta flexible que busca el celular con o sin el signo '+'
    @EntityGraph(attributePaths = { "cargo" })
    @Query("SELECT t FROM Trabajador t WHERE t.celular = :celular OR t.celular = CONCAT('+', :celular) OR CONCAT('+', t.celular) = :celular")
    Optional<Trabajador> findByPersonaCelular(@Param("celular") String celular);

    boolean existsByUsername(String username);

    java.util.List<Trabajador> findByActivoTrue();

    org.springframework.data.domain.Page<Trabajador> findByCargoId(Integer cargoId,
            org.springframework.data.domain.Pageable pageable);
}