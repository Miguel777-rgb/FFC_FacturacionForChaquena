package com.chaquena.backend_logistica.clientes.repository;

import com.chaquena.backend_logistica.clientes.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, UUID>, JpaSpecificationExecutor<Cliente> {

    Optional<Cliente> findByDni(String dni);

    Optional<Cliente> findByCelular(String celular);

    /** Cliente que ya vinculo su cuenta de Discord con {@code /vincular}. */
    Optional<Cliente> findByDiscordUserId(String discordUserId);

    /**
     * Busqueda rapida del mozo con el cliente delante: un solo cuadro de texto
     * que acepta indistintamente telefono, documento, nombre o correo.
     */
    @Query("""
            select c from Cliente c
            where lower(c.dni) like lower(concat('%', :termino, '%'))
               or lower(c.celular) like lower(concat('%', :termino, '%'))
               or lower(c.nombres) like lower(concat('%', :termino, '%'))
               or lower(c.apellidos) like lower(concat('%', :termino, '%'))
               or lower(coalesce(c.correo, '')) like lower(concat('%', :termino, '%'))
            """)
    List<Cliente> buscar(@Param("termino") String termino);
}
