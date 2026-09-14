package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.auth.service.TrabajadorContexto;
import com.chaquena.backend_logistica.inventario.domain.*;
import com.chaquena.backend_logistica.inventario.dto.*;
import com.chaquena.backend_logistica.inventario.repository.*;
import com.chaquena.backend_logistica.inventario.service.InventarioService;
import com.chaquena.backend_logistica.inventario.service.ReglaLotes;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.exception.StockInsuficienteException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Unico punto por el que se mueve el stock. Ningun otro servicio escribe
 * insumos.stock_actual directamente: todo movimiento deja su linea en
 * controles_insumo y toma bloqueo pesimista sobre el insumo, que es lo que
 * evita que dos comandas simultaneas descuadren el inventario.
 *
 * <p>Tambien es el unico que escribe lotes, y por la misma razon: con la fila
 * del insumo bloqueada, el stock y la suma de sus lotes cambian juntos. Lo que
 * suma crea un lote; lo que resta consume primero lo que vence antes; lo que
 * vuelve por una cancelacion regresa a los lotes de los que salio.
 */
@Service
@RequiredArgsConstructor
public class InventarioServiceImpl implements InventarioService {

    private final InsumoRepository insumoRepository;
    private final ControlInsumoRepository controlInsumoRepository;
    private final LoteInsumoRepository loteRepository;
    private final ProveedorRepository proveedorRepository;
    private final PlatilloRepository platilloRepository;
    private final ComplementoPlatilloRepository complementoRepository;
    private final TrabajadorContexto trabajadorContexto;
    private final ResumenLotes resumenLotes;

    /** Lo que trae una entrada para su lote. Cualquiera de los tres puede faltar. */
    private record DatosLote(Proveedor proveedor, BigDecimal costoUnitario, LocalDate fechaVencimiento) {
        static final DatosLote SIN_DATOS = new DatosLote(null, null, null);
    }

    /** A donde va lo que suma: a un lote nuevo, o de vuelta a los lotes de los que salio. */
    private enum Entrada { LOTE_NUEVO, REPOSICION }

    /** El movimiento escrito y, si resto, cuanto costo lo que salio (nulo si no se sabe). */
    private record Resultado(ControlInsumo control, BigDecimal costoConsumido) {
    }

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

        boolean esCompra = tipo == TipoControlInsumoEnum.ENTRADA_COMPRA;
        boolean traeDatosDeLote = request.getProveedorId() != null
                || request.getCostoUnitario() != null
                || request.getFechaVencimiento() != null;
        if (traeDatosDeLote && !esCompra) {
            throw new IllegalArgumentException(
                    "El proveedor, el costo y el vencimiento solo van en una entrada por compra.");
        }
        if (request.getFechaVencimiento() != null && request.getFechaVencimiento().isBefore(ReglaLotes.hoy())) {
            throw new IllegalArgumentException("La fecha de vencimiento ya paso: no se puede comprar algo vencido.");
        }

        BigDecimal delta = esCompra ? request.getCantidad() : request.getCantidad().negate();
        DatosLote datos = esCompra
                ? new DatosLote(proveedorParaCompra(request.getProveedorId()),
                        request.getCostoUnitario(), request.getFechaVencimiento())
                : DatosLote.SIN_DATOS;

        UUID trabajadorId = trabajadorContexto.idActualObligatorio();
        Resultado resultado = aplicarSobre(bloquear(request.getInsumoId()), tipo, delta,
                request.getMotivoObservacion(), trabajadorId, UsuarioActual.username(), datos, Entrada.LOTE_NUEVO);
        return MovimientoResponseDto.fromEntity(resultado.control());
    }

    @Override
    @Transactional
    public MovimientoResponseDto registrarMovimientoInterno(UUID insumoId,
            TipoControlInsumoEnum tipoControl, BigDecimal delta, String motivo,
            UUID trabajadorId, String autor) {
        return MovimientoResponseDto.fromEntity(aplicarSobre(bloquear(insumoId), tipoControl, delta,
                motivo, trabajadorId, autor, DatosLote.SIN_DATOS, Entrada.LOTE_NUEVO).control());
    }

    @Override
    @Transactional
    public MovimientoResponseDto registrarEntradaInterna(UUID insumoId, BigDecimal cantidad, String motivo,
            UUID trabajadorId, String autor, UUID proveedorId, BigDecimal costoUnitario,
            LocalDate fechaVencimiento) {
        DatosLote datos = new DatosLote(proveedorParaCompra(proveedorId), costoUnitario, fechaVencimiento);
        return MovimientoResponseDto.fromEntity(aplicarSobre(bloquear(insumoId),
                TipoControlInsumoEnum.ENTRADA_COMPRA, cantidad, motivo, trabajadorId, autor,
                datos, Entrada.LOTE_NUEVO).control());
    }

    /**
     * El lote cocido hereda el costo de lo crudo que consumio, repartido entre lo
     * que rindio: 10 kg de pollo a S/ 11 que dan 7 kg horneados cuestan
     * S/ 15,71 el kilo. Si algun kilo crudo no tenia costo, el cocido tampoco.
     */
    @Override
    @Transactional
    public List<MovimientoResponseDto> transformar(TransformacionRequestDto request) {
        if (request.getInsumoOrigenId().equals(request.getInsumoDestinoId())) {
            throw new IllegalArgumentException("El insumo de origen y el de destino deben ser distintos.");
        }

        UUID trabajadorId = trabajadorContexto.idActualObligatorio();
        String autor = UsuarioActual.username();
        String motivo = request.getMotivoObservacion() != null && !request.getMotivoObservacion().isBlank()
                ? request.getMotivoObservacion()
                : "Transformacion de cocido";

        Resultado salida = aplicarSobre(bloquear(request.getInsumoOrigenId()),
                TipoControlInsumoEnum.TRANSFORMACION_COCIDO, request.getCantidadConsumida().negate(),
                motivo + " (consumo)", trabajadorId, autor, DatosLote.SIN_DATOS, Entrada.LOTE_NUEVO);

        BigDecimal costoUnitario = salida.costoConsumido() != null
                ? salida.costoConsumido().divide(request.getCantidadObtenida(), 4, RoundingMode.HALF_UP)
                : null;

        Resultado entrada = aplicarSobre(bloquear(request.getInsumoDestinoId()),
                TipoControlInsumoEnum.TRANSFORMACION_COCIDO, request.getCantidadObtenida(),
                motivo + " (rendimiento)", trabajadorId, autor,
                new DatosLote(null, costoUnitario, request.getFechaVencimiento()), Entrada.LOTE_NUEVO);

        return List.of(MovimientoResponseDto.fromEntity(salida.control()),
                MovimientoResponseDto.fromEntity(entrada.control()));
    }

    /**
     * Conteo de cierre: compara lo contado en almacen contra lo que dice el
     * sistema y deja un ajuste por cada descuadre. Los insumos que cuadran no
     * generan movimiento, para no ensuciar el kardex.
     *
     * <p>Lo que falta sale de los lotes que vencen antes; lo que sobra entra como
     * un lote sin costo ni fecha, porque nadie sabe de donde salio.
     */
    @Override
    @Transactional
    public ConteoFisicoResponseDto conteoFisico(ConteoFisicoRequestDto request) {
        UUID trabajadorId = trabajadorContexto.idActualObligatorio();
        String autor = UsuarioActual.username();
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
                    trabajadorId, autor, DatosLote.SIN_DATOS, Entrada.LOTE_NUEVO);

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
        exigirInsumo(insumoId);
        return PageResponseDto.de(
                controlInsumoRepository.findByInsumoIdOrderByDateCreatedDesc(insumoId, pageable),
                MovimientoResponseDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoteInsumoDto> lotes(UUID insumoId, boolean soloDisponibles) {
        exigirInsumo(insumoId);
        List<LoteInsumo> lotes = soloDisponibles
                ? loteRepository.findByInsumoIdAndCantidadRestanteGreaterThan(insumoId, BigDecimal.ZERO)
                : loteRepository.findByInsumoId(insumoId);
        LocalDate hoy = ReglaLotes.hoy();
        return ReglaLotes.ordenFefo(lotes).stream().map(l -> LoteInsumoDto.fromEntity(l, hoy)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InventarioValorizadoDto valorizado() {
        List<Insumo> insumos = insumoRepository.findAll(Sort.by("nombre"));
        Map<UUID, ReglaLotes.Resumen> lotes = resumenLotes.de(insumos.stream().map(Insumo::getId).toList());

        BigDecimal total = BigDecimal.ZERO;
        long conStockSinCosto = 0;
        List<InventarioValorizadoDto.InsumoValorizado> lineas = new ArrayList<>();

        for (Insumo insumo : insumos) {
            ReglaLotes.Resumen r = lotes.get(insumo.getId());
            BigDecimal stock = valor(insumo.getStockActual());
            BigDecimal valor = r != null ? r.valor() : BigDecimal.ZERO;
            BigDecimal conCosto = r != null ? r.cantidadConCosto() : BigDecimal.ZERO;
            BigDecimal sinCosto = stock.subtract(conCosto).max(BigDecimal.ZERO);

            total = total.add(valor);
            if (sinCosto.signum() > 0) conStockSinCosto++;

            lineas.add(InventarioValorizadoDto.InsumoValorizado.builder()
                    .insumoId(insumo.getId())
                    .nombre(insumo.getNombre())
                    .unidadMedida(insumo.getUnidadMedida())
                    .stockActual(stock)
                    .valor(valor)
                    .costoPromedio(conCosto.signum() > 0 ? valor.divide(conCosto, 4, RoundingMode.HALF_UP) : null)
                    .cantidadSinCosto(sinCosto)
                    .build());
        }

        return InventarioValorizadoDto.builder()
                .valorTotal(total.setScale(2, RoundingMode.HALF_UP))
                .insumosConStockSinCosto(conStockSinCosto)
                .insumos(lineas)
                .build();
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
        List<Insumo> todos = insumoRepository.findAll();
        Collection<ReglaLotes.Resumen> lotes = resumenLotes.de(todos.stream().map(Insumo::getId).toList()).values();

        List<ResumenInventarioDto.MovimientosPorTipo> movimientos =
                controlInsumoRepository.resumenPorTipo(desde, hasta).stream()
                        .map(fila -> ResumenInventarioDto.MovimientosPorTipo.builder()
                                .tipoControl((TipoControlInsumoEnum) fila[0])
                                .cantidadMovimientos(((Number) fila[1]).longValue())
                                .volumenTotal((BigDecimal) fila[2])
                                .build())
                        .toList();

        return ResumenInventarioDto.builder()
                .totalInsumos(todos.size())
                .insumosBajoMinimo(alertas.size())
                .insumosVencidos(lotes.stream().filter(r -> r.cantidadVencida().signum() > 0).count())
                .insumosPorVencer(lotes.stream().filter(r -> r.cantidadPorVencer().signum() > 0).count())
                .valorInventario(lotes.stream().map(ReglaLotes.Resumen::valor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP))
                .alertas(resumenLotes.conLotes(alertas))
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

        String autor = UsuarioActual.username();
        for (Insumo insumo : bloqueados) {
            aplicarSobre(insumo, TipoControlInsumoEnum.SALIDA_VENTA, consumo.get(insumo.getId()).negate(),
                    motivo, trabajadorId, autor, DatosLote.SIN_DATOS, Entrada.LOTE_NUEVO);
        }
    }

    @Override
    @Transactional
    public void reponerPorCancelacion(Map<UUID, BigDecimal> consumo, String motivo, UUID trabajadorId) {
        String autor = UsuarioActual.username();
        for (Map.Entry<UUID, BigDecimal> entrada : consumo.entrySet()) {
            aplicarSobre(bloquear(entrada.getKey()), TipoControlInsumoEnum.AJUSTE_AUDITORIA,
                    entrada.getValue(), motivo, trabajadorId, autor, DatosLote.SIN_DATOS, Entrada.REPOSICION);
        }
    }

    // ------------------------------------------------------------------
    // Nucleo: toda escritura de stock y de lotes pasa por aqui
    // ------------------------------------------------------------------

    private Resultado aplicarSobre(Insumo insumo, TipoControlInsumoEnum tipo, BigDecimal delta,
            String motivo, UUID trabajadorId, String autor, DatosLote datos, Entrada entrada) {
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

        ControlInsumo control = controlInsumoRepository.save(ControlInsumo.builder()
                .insumo(insumo)
                .trabajadorId(trabajadorId)
                .tipoControl(tipo)
                .cantidad(delta)
                .stockAnterior(anterior)
                .stockNuevo(nuevo)
                .motivoObservacion(motivo)
                .createdBy(autor)
                .build());

        BigDecimal costoConsumido = null;
        if (delta.signum() > 0) {
            BigDecimal sinLote = delta;
            if (entrada == Entrada.REPOSICION) {
                List<LoteInsumo> consumidos = loteRepository.consumidosDe(insumo.getId());
                sinLote = ReglaLotes.reponer(consumidos, delta);
                loteRepository.saveAll(consumidos);
            }
            if (sinLote.signum() > 0) {
                loteRepository.save(LoteInsumo.builder()
                        .insumo(insumo)
                        .controlOrigen(control)
                        .proveedor(datos.proveedor())
                        .cantidadInicial(sinLote)
                        .cantidadRestante(sinLote)
                        .costoUnitario(datos.costoUnitario())
                        .fechaVencimiento(datos.fechaVencimiento())
                        .createdBy(autor)
                        .build());
            }
        } else if (delta.signum() < 0) {
            List<LoteInsumo> disponibles = loteRepository
                    .findByInsumoIdAndCantidadRestanteGreaterThan(insumo.getId(), BigDecimal.ZERO);
            List<ReglaLotes.Toma> tomas = ReglaLotes.consumirFefo(disponibles, delta.negate());
            loteRepository.saveAll(disponibles);
            costoConsumido = ReglaLotes.costoDe(tomas, delta.negate());
        }

        return new Resultado(control, costoConsumido);
    }

    /** El proveedor de una compra: opcional, pero si viene tiene que existir y estar activo. */
    private Proveedor proveedorParaCompra(UUID proveedorId) {
        if (proveedorId == null) return null;
        Proveedor proveedor = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el proveedor", proveedorId));
        if (!Boolean.TRUE.equals(proveedor.getActivo())) {
            throw new ConflictoException("El proveedor " + proveedor.getNombre() + " esta dado de baja.");
        }
        return proveedor;
    }

    private void exigirInsumo(UUID insumoId) {
        if (!insumoRepository.existsById(insumoId)) {
            throw RecursoNoEncontradoException.de("el insumo", insumoId);
        }
    }

    private Insumo bloquear(UUID insumoId) {
        return insumoRepository.findByIdParaActualizar(insumoId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el insumo", insumoId));
    }

    private BigDecimal valor(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
