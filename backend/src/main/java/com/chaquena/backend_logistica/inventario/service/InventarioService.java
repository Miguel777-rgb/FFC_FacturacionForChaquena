package com.chaquena.backend_logistica.inventario.service;

import com.chaquena.backend_logistica.inventario.dto.*;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface InventarioService {

    MovimientoResponseDto registrarMovimiento(MovimientoRequestDto request);

    /**
     * Entrada interna para procesos sin sesion HTTP, como el bot de stock.
     * El delta va con signo y el autor se pasa explicitamente, porque no hay
     * token del que deducirlo. Comparte el mismo bloqueo y el mismo kardex que
     * el resto: no es una via paralela para escribir stock.
     */
    MovimientoResponseDto registrarMovimientoInterno(java.util.UUID insumoId,
            com.chaquena.backend_logistica.inventario.domain.TipoControlInsumoEnum tipoControl,
            java.math.BigDecimal delta, String motivo, java.util.UUID trabajadorId, String autor);

    List<MovimientoResponseDto> transformar(TransformacionRequestDto request);

    ConteoFisicoResponseDto conteoFisico(ConteoFisicoRequestDto request);

    PageResponseDto<MovimientoResponseDto> kardex(UUID insumoId, Pageable pageable);

    DisponibilidadResponseDto verificarDisponibilidad(DisponibilidadRequestDto request);

    ResumenInventarioDto resumen(ZonedDateTime desde, ZonedDateTime hasta);

    /**
     * Explota las recetas de un carrito y devuelve el consumo agregado por
     * insumo. Lo usan tanto la verificacion previa como la creacion de la
     * comanda, para que ambas cuenten exactamente igual.
     */
    Map<UUID, BigDecimal> calcularConsumo(List<DisponibilidadRequestDto.ItemDisponibilidad> items);

    /** Descuento de stock por venta. Falla con 422 si no alcanza. */
    void descontarPorVenta(Map<UUID, BigDecimal> consumo, String motivo, UUID trabajadorId);

    /** Reposicion de stock al cancelar una comanda o quitar una linea. */
    void reponerPorCancelacion(Map<UUID, BigDecimal> consumo, String motivo, UUID trabajadorId);
}
