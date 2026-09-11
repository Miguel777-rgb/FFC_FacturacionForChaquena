package com.chaquena.backend_logistica.shared.mensajeria.service;

import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.delivery.repository.SesionBotRepository;
import com.chaquena.backend_logistica.shared.mensajeria.MensajeriaService;
import com.chaquena.backend_logistica.shared.mensajeria.dto.EstadoBotsDto;
import com.chaquena.backend_logistica.shared.mensajeria.dto.VinculacionBotDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Junta en una sola foto lo que hace falta para saber si los bots sirven.
 *
 * <p>La pregunta "¿funcionan los bots?" no tiene una respuesta sino tres, y
 * viven en sitios distintos: el proveedor y sus conexiones los conoce el
 * adaptador de mensajeria, quien puede usarlos esta en la tabla de
 * trabajadores, y las conversaciones a medio hacer en la de sesiones. Este
 * servicio existe para que la pantalla no tenga que saber eso.
 */
@Service
@RequiredArgsConstructor
public class EstadoBotsService {

    private final MensajeriaService mensajeria;
    private final TrabajadorRepository trabajadorRepository;
    private final SesionBotRepository sesionBotRepository;

    @Transactional(readOnly = true)
    public EstadoBotsDto estado() {
        List<VinculacionBotDto> vinculaciones =
                trabajadorRepository.findByDiscordUserIdIsNotNullOrderByUsernameAsc().stream()
                        .map(VinculacionBotDto::fromEntity)
                        .toList();

        return EstadoBotsDto.builder()
                .proveedorPedido(mensajeria.proveedorPedido())
                .proveedorActivo(mensajeria.proveedor())
                .proveedoresDisponibles(mensajeria.proveedoresDisponibles().stream().sorted().toList())
                .disponible(mensajeria.disponible())
                .maxOpciones(mensajeria.maxOpciones())
                .canales(mensajeria.estado())
                .trabajadoresVinculados(trabajadorRepository.countByDiscordUserIdIsNotNull())
                .trabajadoresActivos(trabajadorRepository.countByActivoTrue())
                .sesionesClienteAbiertas(sesionBotRepository.countByExpiraEnAfter(ZonedDateTime.now()))
                .vinculaciones(vinculaciones)
                .build();
    }
}
