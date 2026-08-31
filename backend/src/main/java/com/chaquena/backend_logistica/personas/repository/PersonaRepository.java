package com.chaquena.backend_logistica.personas.repository;

import com.chaquena.backend_logistica.personas.domain.Persona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonaRepository extends JpaRepository<Persona, UUID> {

    boolean existsByDni(String dni);

    boolean existsByCorreo(String correo);

    boolean existsByCelular(String celular);

    Optional<Persona> findByDni(String dni);

    Optional<Persona> findByCorreo(String correo);

    Optional<Persona> findByCelular(String celular);
}
