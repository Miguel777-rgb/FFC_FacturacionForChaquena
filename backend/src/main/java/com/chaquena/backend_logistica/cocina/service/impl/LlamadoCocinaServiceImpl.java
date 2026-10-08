package com.chaquena.backend_logistica.cocina.service.impl;

import com.chaquena.backend_logistica.cocina.domain.EstadoLlamadoEnum;
import com.chaquena.backend_logistica.cocina.domain.LlamadoCocina;
import com.chaquena.backend_logistica.cocina.dto.LlamadoCocinaDto;
import com.chaquena.backend_logistica.cocina.repository.LlamadoCocinaRepository;
import com.chaquena.backend_logistica.cocina.service.LlamadoCocinaService;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.pedidos.repository.OrdenRepository;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import com.chaquena.backend_logistica.shared.websocket.UsuarioStomp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static com.chaquena.backend_logistica.cocina.domain.EstadoLlamadoEnum.ATENDIDO;
import static com.chaquena.backend_logistica.cocina.domain.EstadoLlamadoEnum.CERRADO;
import static com.chaquena.backend_logistica.cocina.domain.EstadoLlamadoEnum.PENDIENTE;

@Service
@RequiredArgsConstructor
public class LlamadoCocinaServiceImpl implements LlamadoCocinaService {

    private final LlamadoCocinaRepository llamadoRepository;
    private final OrdenRepository ordenRepository;

    @Override
    @Transactional
    public LlamadoCocinaDto llamar(UUID ordenId, UsuarioStomp quien) {
        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la comanda", ordenId));
        // Llamar significa «hay que recoger»: solo tiene sentido con el plato en el pase.
        if (orden.getEstado() != EstadoOrdenEnum.EN_PREPARACION
                || !Boolean.TRUE.equals(orden.getFlagCierrePlatillo())) {
            throw new ConflictoException("Solo se llama al mozo cuando la comanda esta lista.");
        }
        return llamadoRepository.findFirstByOrdenIdAndEstado(ordenId, PENDIENTE)
                .map(LlamadoCocinaDto::de)
                .orElseGet(() -> LlamadoCocinaDto.de(llamadoRepository.save(LlamadoCocina.builder()
                        .orden(orden)
                        .llamadoPor(quien.username())
                        .llamadoPorNombre(quien.nombre())
                        .llamadoEn(ZonedDateTime.now())
                        .estado(PENDIENTE)
                        .build())));
    }

    @Override
    @Transactional
    public LlamadoCocinaDto atender(UUID llamadoId, UsuarioStomp mozo) {
        int filas = llamadoRepository.atender(
                llamadoId, mozo.username(), mozo.nombre(), ZonedDateTime.now(), PENDIENTE, ATENDIDO);
        LlamadoCocina llamado = llamadoRepository.findById(llamadoId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el llamado", llamadoId));
        if (filas == 0) {
            throw new ConflictoException(llamado.getEstado() == ATENDIDO
                    ? llamado.getAtendidoPorNombre() + " ya va en camino."
                    : "Este llamado ya se cerro.");
        }
        return LlamadoCocinaDto.de(llamado);
    }

    /**
     * En una transaccion propia: lo llama el oyente cuando la del cambio de
     * estado ya se confirmo y no queda ninguna abierta.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<LlamadoCocinaDto> cerrarPendientesDe(UUID ordenId) {
        List<LlamadoCocina> pendientes = llamadoRepository.findByOrdenIdAndEstado(ordenId, PENDIENTE);
        if (pendientes.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = pendientes.stream().map(LlamadoCocina::getId).toList();
        llamadoRepository.cerrarPendientesDe(ordenId, ZonedDateTime.now(), UsuarioActual.username(),
                PENDIENTE, CERRADO);
        return llamadoRepository.findAllById(ids).stream().map(LlamadoCocinaDto::de).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LlamadoCocinaDto> estadoPara(UsuarioStomp usuario) {
        List<LlamadoCocinaDto> enElPase = llamadoRepository
                .deComandasEnElPase(EstadoOrdenEnum.EN_PREPARACION, CERRADO).stream()
                .map(LlamadoCocinaDto::de)
                .toList();
        if (usuario.tieneAlgunRol("COCINA", "ADMIN")) {
            return enElPase;
        }
        return enElPase.stream().filter(l -> l.estado() == EstadoLlamadoEnum.PENDIENTE).toList();
    }
}
