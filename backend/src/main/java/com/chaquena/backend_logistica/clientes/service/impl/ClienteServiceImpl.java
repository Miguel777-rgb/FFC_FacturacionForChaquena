package com.chaquena.backend_logistica.clientes.service.impl;

import com.chaquena.backend_logistica.clientes.domain.Cliente;
import com.chaquena.backend_logistica.clientes.dto.*;
import com.chaquena.backend_logistica.clientes.repository.ClienteRepository;
import com.chaquena.backend_logistica.clientes.service.ClienteService;
import com.chaquena.backend_logistica.pedidos.repository.OrdenDetalleRepository;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClienteServiceImpl implements ClienteService {

    private static final String PREFIJO_ANONIMO = "ANON-";

    private final ClienteRepository clienteRepository;
    private final OrdenDetalleRepository ordenDetalleRepository;

    /**
     * Un solo cuadro de texto que acepta telefono, documento, nombre o correo:
     * es lo que usa el mozo con el cliente delante, y el orden importa menos
     * que la velocidad.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ClienteResponseDto> buscar(String termino) {
        if (termino == null || termino.isBlank()) {
            throw new IllegalArgumentException("Indica un telefono, documento o nombre para buscar.");
        }
        return clienteRepository.buscar(termino.trim()).stream()
                .map(ClienteResponseDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<ClienteResponseDto> listar(Pageable pageable) {
        return PageResponseDto.de(clienteRepository.findAll(pageable), ClienteResponseDto::fromEntity);
    }

    @Override
    @Transactional
    public ClienteResponseDto crear(ClienteRequestDto request) {
        clienteRepository.findByDni(request.getDni()).ifPresent(existente -> {
            throw new ConflictoException("Ya existe un cliente con el documento " + request.getDni() + ".");
        });

        Cliente cliente = Cliente.builder()
                .dni(request.getDni())
                .nombres(request.getNombres())
                .apellidos(request.getApellidos())
                .correo(vacioANulo(request.getCorreo()))
                .celular(vacioANulo(request.getCelular()))
                .direccionHabitual(request.getDireccionHabitual())
                .tipoCliente(request.getTipoCliente())
                .puntosFidelidad(0)
                .scoreFraude(0)
                .bloqueadoPorFraude(false)
                .createdBy(UsuarioActual.username())
                .build();

        return ClienteResponseDto.fromEntity(clienteRepository.save(cliente));
    }

    /**
     * Cliente que no quiere identificarse. Se le asigna un documento
     * provisional porque la tabla personas exige dni unico, pero queda
     * reconocible por el prefijo.
     */
    @Override
    @Transactional
    public ClienteResponseDto crearAnonimo(ClienteAnonimoRequestDto request) {
        String dniProvisional = PREFIJO_ANONIMO
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Cliente cliente = Cliente.builder()
                .dni(dniProvisional)
                .nombres(request.getNombreReferencia() != null && !request.getNombreReferencia().isBlank()
                        ? request.getNombreReferencia()
                        : "Cliente")
                .apellidos("Sin identificar")
                .celular(vacioANulo(request.getCelular()))
                .discordUserId(vacioANulo(request.getDiscordUserId()))
                .direccionHabitual(request.getDireccionHabitual())
                .puntosFidelidad(0)
                .scoreFraude(0)
                .bloqueadoPorFraude(false)
                .createdBy(UsuarioActual.username())
                .build();

        return ClienteResponseDto.fromEntity(clienteRepository.save(cliente));
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponseDto obtenerPorId(UUID id) {
        return ClienteResponseDto.fromEntity(buscarEntidad(id));
    }

    @Override
    @Transactional
    public ClienteResponseDto actualizar(UUID id, ClienteRequestDto request) {
        Cliente cliente = buscarEntidad(id);
        clienteRepository.findByDni(request.getDni())
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> {
                    throw new ConflictoException(
                            "Otro cliente ya usa el documento " + request.getDni() + ".");
                });

        cliente.setDni(request.getDni());
        cliente.setNombres(request.getNombres());
        cliente.setApellidos(request.getApellidos());
        cliente.setCorreo(vacioANulo(request.getCorreo()));
        cliente.setCelular(vacioANulo(request.getCelular()));
        cliente.setDireccionHabitual(request.getDireccionHabitual());
        cliente.setTipoCliente(request.getTipoCliente());
        cliente.setModifiedBy(UsuarioActual.username());

        return ClienteResponseDto.fromEntity(clienteRepository.save(cliente));
    }

    @Override
    @Transactional(readOnly = true)
    public PreferenciasClienteDto preferencias(UUID id) {
        buscarEntidad(id);
        return PreferenciasClienteDto.builder()
                .clienteId(id)
                .notasHabituales(ordenDetalleRepository.notasHistoricasDelCliente(id))
                .platillosFrecuentes(List.of())
                .build();
    }

    @Override
    @Transactional
    public ClienteResponseDto cambiarBloqueoFraude(UUID id, boolean bloqueado, String motivo) {
        Cliente cliente = buscarEntidad(id);
        cliente.setBloqueadoPorFraude(bloqueado);
        if (bloqueado) {
            int score = cliente.getScoreFraude() != null ? cliente.getScoreFraude() : 0;
            cliente.setScoreFraude(Math.max(score, 100));
        }
        cliente.setModifiedBy(UsuarioActual.username());
        return ClienteResponseDto.fromEntity(clienteRepository.save(cliente));
    }

    private Cliente buscarEntidad(UUID id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el cliente", id));
    }

    /** El correo y el celular son unicos: un string vacio chocaria con otro vacio. */
    private String vacioANulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
