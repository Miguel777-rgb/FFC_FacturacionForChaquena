package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.auth.service.TrabajadorContexto;
import com.chaquena.backend_logistica.inventario.domain.*;
import com.chaquena.backend_logistica.inventario.dto.*;
import com.chaquena.backend_logistica.inventario.repository.*;
import com.chaquena.backend_logistica.inventario.service.InventarioService;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.exception.StockInsuficienteException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Unico punto por el que se mueve el stock. Ningun otro servicio escribe
 * insumos.stock_actual directamente: todo movimiento deja su linea en
 * controles_insumo y toma bloqueo pesimista sobre el insumo, que es lo que
 * evita que dos comandas simultaneas descuadren el inventario.
 */
@Service
@RequiredArgsConstructor
public class InventarioServiceImpl implements InventarioService {

    private final InsumoRepository insumoRepository;
    private final ControlInsumoRepository controlInsumoRepository;
    private final PlatilloRepository platilloRepository;
    private final ComplementoPlatilloRepository complementoRepository;
    private final TrabajadorContexto trabajadorContexto;

    @Override
    @Transactional
    public MovimientoResponseDto registrarMovimiento(MovimientoRequestDto request) {
        TipoControlInsumoEnum tipo = request.getTipoControl();

        if (tipo == TipoControlInsumoEnum.TRANSFORMACION_COCIDO) {
            throw new ConflictoException(
                    "Una transformacion mueve dos insumos a la vez. Usa POST /api/v1/inventario/transformaciones.");
        }
        if (tipo == TipoControlInsumoEnum.SALIDA_VENTA) {
            throw new ConflictoException(
                    "Las salidas por venta las genera el sistema al crear la comanda, no se registran a mano.");
        }
        if (tipo == TipoControlInsumoEnum.AJUSTE_AUDITORIA) {
            throw new ConflictoException(
                    "Los ajustes salen del conteo fisico. Usa POST /api/v1/inventario/conteo-fisico.");
        }

        BigDecimal delta = tipo == TipoControlInsumoEnum.ENTRADA_COMPRA
                ? request.getCantidad()
                : request.getCantidad().negate();

        UUID trabajadorId = trabajadorContexto.idActualObligatorio();
        ControlInsumo control = aplicar(request.getInsumoId(), tipo, delta,
                request.getMotivoObservacion(), trabajadorId);
        return MovimientoResponseDto.fromEntity(control);
    }

    @Override
    @Transactional
    public MovimientoResponseDto registrarMovimientoInterno(UUID insumoId,
            TipoControlInsumoEnum tipoControl, BigDecimal delta, String motivo,
            UUID trabajadorId, String autor) {
        return MovimientoResponseDto.fromEntity(
                aplicarSobre(bloquear(insumoId), tipoControl, delta, motivo, trabajadorId, autor));
    }

    @Override
    @Transactional
    public List<MovimientoResponseDto> transformar(TransformacionRequestDto request) {
        if (request.getInsumoOrigenId().equals(request.getInsumoDestinoId())) {
            throw new IllegalArgumentException("El insumo de origen y el de destino deben ser distintos.");
        }

        UUID trabajadorId = trabajadorContexto.idActualObligatorio();
        String motivo = request.getMotivoObservacion() != null && !request.getMotivoObservacion().isBlank()
                ? request.getMotivoObservacion()
                : "Transformacion de cocido";

        ControlInsumo salida = aplicar(request.getInsumoOrigenId(),
                TipoControlInsumoEnum.TRANSFORMACION_COCIDO,
                request.getCantidadConsumida().negate(), motivo + " (consumo)", trabajadorId);

        ControlInsumo entrada = aplicar(request.getInsumoDestinoId(),
                TipoControlInsumoEnum.TRANSFORMACION_COCIDO,
                request.getCantidadObtenida(), motivo + " (rendimiento)", trabajadorId);

        return List.of(MovimientoResponseDto.fromEntity(salida), MovimientoResponseDto.fromEntity(entrada));
    }

    /**
     * Conteo de cierre: compara lo contado en almacen contra lo que dice el
     * sistema y deja un ajuste por cada descuadre. Los insumos que cuadran no
     * generan movimiento, para no ensuciar el kardex.
     */
    @Override
    @Transactional
    public ConteoFisicoResponseDto conteoFisico(ConteoFisicoRequestDto request) {
        UUID trabajadorId = trabajadorContexto.idActualObligatorio();
        String observacion = request.getObservacion() != null && !request.getObservacion().isBlank()
                ? request.getObservacion()
                : "Conteo fisico de cierre";

        List<ConteoFisicoResponseDto.Descuadre> descuadres = new ArrayList<>();

        for (ConteoFisicoRequestDto.ItemConteo item : request.getItems()) {
            Insumo insumo = bloquear(item.getInsumoId());
            BigDecimal enSistema = valor(insumo.getStockActual());
            BigDecimal contado = item.getCantidadContada();
            BigDecimal diferencia = contado.subtract(enSistema);

            if (diferencia.signum() == 0) {
                continue;
            }

            aplicarSobre(insumo, TipoControlInsumoEnum.AJUSTE_AUDITORIA, diferencia,
                    observacion + " (sistema " + enSistema.toPlainString()
                            + ", contado " + contado.toPlainString() + ")",
                    trabajadorId);

            descuadres.add(ConteoFisicoResponseDto.Descuadre.builder()
                    .insumoId(insumo.getId())
                    .insumoNombre(insumo.getNombre())
                    .unidadMedida(insumo.getUnidadMedida())
                    .stockSistema(enSistema)
                    .stockContado(contado)
                    .diferencia(diferencia)
                    .build());
        }

        return ConteoFisicoResponseDto.builder()
                .insumosContados(request.getItems().size())
                .insumosAjustados(descuadres.size())
                .descuadres(descuadres)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<MovimientoResponseDto> kardex(UUID insumoId, Pageable pageable) {
        if (!insumoRepository.existsById(insumoId)) {
            throw RecursoNoEncontradoException.de("el insumo", insumoId);
        }
        return PageResponseDto.de(
                controlInsumoRepository.findByInsumoIdOrderByDateCreatedDesc(insumoId, pageable),
                MovimientoResponseDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public DisponibilidadResponseDto verificarDisponibilidad(DisponibilidadRequestDto request) {
        Map<UUID, BigDecimal> consumo = calcularConsumo(request.getItems());
        List<DisponibilidadResponseDto.Faltante> faltantes = new ArrayList<>();

        for (Map.Entry<UUID, BigDecimal> entrada : consumo.entrySet()) {
            Insumo insumo = insumoRepository.findById(entrada.getKey())
                    .orElseThrow(() -> RecursoNoEncontradoException.de("el insumo", entrada.getKey()));
            BigDecimal disponible = valor(insumo.getStockActual());
            BigDecimal requerido = entrada.getValue();
            if (disponible.compareTo(requerido) < 0) {
                faltantes.add(DisponibilidadResponseDto.Faltante.builder()
                        .insumoId(insumo.getId())
                        .insumoNombre(insumo.getNombre())
                        .unidadMedida(insumo.getUnidadMedida())
                        .requerido(requerido)
                        .disponible(disponible)
                        .faltante(requerido.subtract(disponible))
                        .build());
            }
        }

        return DisponibilidadResponseDto.builder()
                .disponible(faltantes.isEmpty())
                .faltantes(faltantes)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenInventarioDto resumen(ZonedDateTime desde, ZonedDateTime hasta) {
        List<Insumo> alertas = insumoRepository.bajoMinimo();

        List<ResumenInventarioDto.MovimientosPorTipo> movimientos =
                controlInsumoRepository.resumenPorTipo(desde, hasta).stream()
                        .map(fila -> ResumenInventarioDto.MovimientosPorTipo.builder()
                                .tipoControl((TipoControlInsumoEnum) fila[0])
                                .cantidadMovimientos(((Number) fila[1]).longValue())
                                .volumenTotal((BigDecimal) fila[2])
                                .build())
                        .toList();

        return ResumenInventarioDto.builder()
                .totalInsumos(insumoRepository.count())
                .insumosBajoMinimo(alertas.size())
                .alertas(alertas.stream().map(InsumoResponseDto::fromEntity).toList())
                .movimientos(movimientos)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> calcularConsumo(List<DisponibilidadRequestDto.ItemDisponibilidad> items) {
        Map<UUID, BigDecimal> consumo = new LinkedHashMap<>();

        for (DisponibilidadRequestDto.ItemDisponibilidad item : items) {
            Platillo platillo = platilloRepository.findWithRecetaById(item.getPlatilloId())
                    .orElseThrow(() -> RecursoNoEncontradoException.de("el platillo", item.getPlatilloId()));

            BigDecimal cantidad = BigDecimal.valueOf(item.getCantidad());
            for (InsumoPlatillo linea : platillo.getReceta()) {
                if (linea.getCantidadRequerida() == null) {
                    continue;
                }
                consumo.merge(linea.getInsumo().getId(),
                        linea.getCantidadRequerida().multiply(cantidad), BigDecimal::add);
            }

            if (item.getComplementoIds() != null) {
                for (UUID complementoId : item.getComplementoIds()) {
                    ComplementoPlatillo complemento = complementoRepository.findById(complementoId)
                            .orElseThrow(() -> RecursoNoEncontradoException.de("el complemento", complementoId));
                    if (complemento.getInsumoAsociado() != null) {
                        consumo.merge(complemento.getInsumoAsociado().getId(), cantidad, BigDecimal::add);
                    }
                }
            }
        }
        return consumo;
    }

    @Override
    @Transactional
    public void descontarPorVenta(Map<UUID, BigDecimal> consumo, String motivo, UUID trabajadorId) {
        List<String> faltantes = new ArrayList<>();
        List<Insumo> bloqueados = new ArrayList<>();

        // Primero se verifica todo con las filas ya bloqueadas; recien despues
        // se escribe. Asi una comanda no queda a medio descontar.
        for (Map.Entry<UUID, BigDecimal> entrada : consumo.entrySet()) {
            Insumo insumo = bloquear(entrada.getKey());
            bloqueados.add(insumo);
            BigDecimal disponible = valor(insumo.getStockActual());
            if (disponible.compareTo(entrada.getValue()) < 0) {
                faltantes.add(insumo.getNombre() + ": se necesitan "
                        + entrada.getValue().toPlainString() + " " + insumo.getUnidadMedida()
                        + " y solo hay " + disponible.toPlainString() + ".");
            }
        }

        if (!faltantes.isEmpty()) {
            throw new StockInsuficienteException(
                    "No hay insumos suficientes para preparar la comanda.", faltantes);
        }

        for (Insumo insumo : bloqueados) {
            aplicarSobre(insumo, TipoControlInsumoEnum.SALIDA_VENTA,
                    consumo.get(insumo.getId()).negate(), motivo, trabajadorId);
        }
    }

    @Override
    @Transactional
    public void reponerPorCancelacion(Map<UUID, BigDecimal> consumo, String motivo, UUID trabajadorId) {
        for (Map.Entry<UUID, BigDecimal> entrada : consumo.entrySet()) {
            Insumo insumo = bloquear(entrada.getKey());
            aplicarSobre(insumo, TipoControlInsumoEnum.AJUSTE_AUDITORIA, entrada.getValue(),
                    motivo, trabajadorId);
        }
    }

    // ------------------------------------------------------------------
    // Nucleo: toda escritura de stock pasa por aqui
    // ------------------------------------------------------------------

    private ControlInsumo aplicar(UUID insumoId, TipoControlInsumoEnum tipo, BigDecimal delta,
            String motivo, UUID trabajadorId) {
        return aplicarSobre(bloquear(insumoId), tipo, delta, motivo, trabajadorId);
    }

    private ControlInsumo aplicarSobre(Insumo insumo, TipoControlInsumoEnum tipo, BigDecimal delta,
            String motivo, UUID trabajadorId) {
        return aplicarSobre(insumo, tipo, delta, motivo, trabajadorId, UsuarioActual.username());
    }

    private ControlInsumo aplicarSobre(Insumo insumo, TipoControlInsumoEnum tipo, BigDecimal delta,
            String motivo, UUID trabajadorId, String autor) {
        BigDecimal anterior = valor(insumo.getStockActual());
        BigDecimal nuevo = anterior.add(delta);

        if (nuevo.signum() < 0) {
            throw new StockInsuficienteException(
                    "El movimiento dejaria el stock de " + insumo.getNombre() + " en negativo.",
                    List.of(insumo.getNombre() + ": hay " + anterior.toPlainString() + " "
                            + insumo.getUnidadMedida() + " y se intentan retirar "
                            + delta.abs().toPlainString() + "."));
        }

        insumo.setStockActual(nuevo);
        insumo.setModifiedBy(autor);
        insumoRepository.save(insumo);

        ControlInsumo control = ControlInsumo.builder()
                .insumo(insumo)
                .trabajadorId(trabajadorId)
                .tipoControl(tipo)
                .cantidad(delta)
                .stockAnterior(anterior)
                .stockNuevo(nuevo)
                .motivoObservacion(motivo)
                .createdBy(autor)
                .build();

        return controlInsumoRepository.save(control);
    }

    private Insumo bloquear(UUID insumoId) {
        return insumoRepository.findByIdParaActualizar(insumoId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el insumo", insumoId));
    }

    private BigDecimal valor(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
