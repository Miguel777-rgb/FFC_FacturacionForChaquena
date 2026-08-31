package com.chaquena.backend_logistica.pedidos.service;

import com.chaquena.backend_logistica.inventario.dto.PromocionResponseDto;
import com.chaquena.backend_logistica.pedidos.domain.CanalOrigenEnum;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.dto.*;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public interface OrdenService {

    OrdenResponseDto crear(CrearOrdenRequestDto request);

    /**
     * Crea la comanda en nombre de un trabajador que no tiene sesion HTTP.
     *
     * <p>Es la puerta del bot del mozo: la persona esta identificada —vinculo su
     * cuenta de chat con su correo— pero no hay token en el hilo, asi que el
     * autor no se puede deducir del contexto de seguridad y hay que pasarlo. Sin
     * esto, la comanda del mozo quedaria firmada por el bot y las comisiones y el
     * kardex dirian que la tomo un robot.
     */
    OrdenResponseDto crearComoTrabajador(CrearOrdenRequestDto request, UUID trabajadorId, String username);

    PageResponseDto<OrdenResumenDto> buscar(EstadoOrdenEnum estado, CanalOrigenEnum canal,
            TipoOrdenEnum tipoOrden, UUID clienteId, String mesaNumero,
            ZonedDateTime desde, ZonedDateTime hasta, Pageable pageable);

    List<OrdenResumenDto> activas();

    OrdenResponseDto obtenerPorId(UUID id);

    OrdenResponseDto agregarDetalle(UUID ordenId, ItemOrdenRequestDto item);

    OrdenResponseDto actualizarDetalle(UUID ordenId, UUID detalleId, ItemOrdenRequestDto item);

    OrdenResponseDto eliminarDetalle(UUID ordenId, UUID detalleId);

    List<PromocionResponseDto> promocionesAplicables(UUID ordenId);

    OrdenResponseDto aplicarPromocion(UUID ordenId, AplicarPromocionRequestDto request);

    OrdenResponseDto cambiarEstado(UUID ordenId, CambioEstadoRequestDto request);

    OrdenResponseDto actualizarFlags(UUID ordenId, FlagsRequestDto request);

    OrdenResponseDto cancelar(UUID ordenId, CancelarOrdenRequestDto request);

    TicketCocinaDto ticket(UUID ordenId);

    List<OrdenResumenDto> historialDelCliente(UUID clienteId);
}
