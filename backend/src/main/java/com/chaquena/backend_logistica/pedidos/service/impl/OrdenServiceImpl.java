package com.chaquena.backend_logistica.pedidos.service.impl;

import com.chaquena.backend_logistica.auth.service.TrabajadorContexto;
import com.chaquena.backend_logistica.clientes.domain.Cliente;
import com.chaquena.backend_logistica.clientes.repository.ClienteRepository;
import com.chaquena.backend_logistica.fidelizacion.domain.Cupon;
import com.chaquena.backend_logistica.fidelizacion.domain.EstadoCuponEnum;
import com.chaquena.backend_logistica.fidelizacion.repository.CuponRepository;
import com.chaquena.backend_logistica.inventario.domain.ComplementoPlatillo;
import com.chaquena.backend_logistica.inventario.domain.Platillo;
import com.chaquena.backend_logistica.inventario.domain.Promocion;
import com.chaquena.backend_logistica.inventario.dto.DisponibilidadRequestDto;
import com.chaquena.backend_logistica.inventario.dto.PromocionResponseDto;
import com.chaquena.backend_logistica.inventario.repository.ComplementoPlatilloRepository;
import com.chaquena.backend_logistica.inventario.repository.PlatilloRepository;
import com.chaquena.backend_logistica.inventario.repository.PromocionRepository;
import com.chaquena.backend_logistica.inventario.service.InventarioService;
import com.chaquena.backend_logistica.inventario.service.PromocionService;
import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.domain.Mesa;
import com.chaquena.backend_logistica.mesas.repository.MesaRepository;
import com.chaquena.backend_logistica.outbox.service.OutboxService;
import com.chaquena.backend_logistica.pedidos.domain.*;
import com.chaquena.backend_logistica.pedidos.dto.*;
import com.chaquena.backend_logistica.pedidos.repository.OrdenRepository;
import com.chaquena.backend_logistica.pedidos.service.MaquinaEstadosOrden;
import com.chaquena.backend_logistica.pedidos.service.OrdenService;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrdenServiceImpl implements OrdenService {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String EVENTO_FACTURA = "FACTURA_REQUERIDA";

    private final OrdenRepository ordenRepository;
    private final ClienteRepository clienteRepository;
    private final PlatilloRepository platilloRepository;
    private final ComplementoPlatilloRepository complementoRepository;
    private final PromocionRepository promocionRepository;
    private final PromocionService promocionService;
    private final MesaRepository mesaRepository;
    private final CuponRepository cuponRepository;
    private final InventarioService inventarioService;
    private final OutboxService outboxService;
    private final MaquinaEstadosOrden maquinaEstados;
    private final TrabajadorContexto trabajadorContexto;
    private final ApplicationEventPublisher eventos;

    /**
     * Crear la comanda es la operacion critica del POS y ocurre entera dentro
     * de una transaccion: valida stock, descuenta insumos por receta, calcula
     * totales, genera el OTP de delivery y escribe el evento de outbox. Si algo
     * falla, no queda ni media comanda ni stock descontado.
     */
    @Override
    @Transactional
    public OrdenResponseDto crear(CrearOrdenRequestDto request) {
        return crearInterno(request, null, null);
    }

    @Override
    @Transactional
    public OrdenResponseDto crearComoTrabajador(CrearOrdenRequestDto request, UUID trabajadorId,
            String username) {
        return crearInterno(request, trabajadorId, username);
    }

    private OrdenResponseDto crearInterno(CrearOrdenRequestDto request, UUID autorExplicito,
            String usernameExplicito) {
        Cliente cliente = resolverCliente(request.getClienteId());
        validarPorFraude(cliente);

        Mesa mesa = resolverMesa(request);
        validarDelivery(request);

        CanalOrigenEnum canal = request.getCanalOrigen() != null
                ? request.getCanalOrigen()
                : CanalOrigenEnum.POS;
        UUID autor = autorExplicito != null ? autorExplicito : resolverAutor(canal);
        String registradoPor = usernameExplicito != null ? usernameExplicito : UsuarioActual.username();

        Orden orden = Orden.builder()
                .cliente(cliente)
                .mozoId(autor)
                .tipoOrden(request.getTipoOrden())
                .canalOrigen(canal)
                .mesa(mesa)
                .mesaNumero(mesa != null ? mesa.getNumero() : null)
                .direccionDelivery(request.getDireccionDelivery())
                .tipoPago(request.getTipoPago())
                .estado(EstadoOrdenEnum.ENCOLADO)
                .scoringRiesgoOrden(cliente != null && cliente.getScoreFraude() != null
                        ? cliente.getScoreFraude() : 0)
                .tiempoInicioGlobal(ZonedDateTime.now())
                .montoSubtotal(BigDecimal.ZERO)
                .montoTotal(BigDecimal.ZERO)
                .createdBy(registradoPor)
                .build();

        for (ItemOrdenRequestDto item : request.getItems()) {
            orden.addDetalle(construirDetalle(item));
        }

        aplicarDescuentos(orden, request.getPromocionId(), request.getCuponCodigo());

        if (request.getTipoOrden() == TipoOrdenEnum.DELIVERY) {
            orden.setCodigoOtpEntrega(generarOtp());
        }

        // El stock se descuenta con las recetas ya explotadas del carrito.
        inventarioService.descontarPorVenta(consumoDe(request.getItems()),
                "Salida por comanda", autor);

        Orden guardada = ordenRepository.save(orden);

        if (mesa != null) {
            mesa.setEstado(EstadoMesaEnum.OCUPADA);
            mesa.setModifiedBy(UsuarioActual.username());
            mesaRepository.save(mesa);
        }

        outboxService.registrar(EVENTO_FACTURA, "ORDEN", guardada.getId().toString(),
                payloadFacturacion(guardada));

        // Se entrega despues del commit: cocina no debe ver comandas que aun
        // podrian deshacerse por falta de stock.
        eventos.publishEvent(new OrdenCreadaEvent(guardada.getId()));

        log.info("Comanda {} creada por {} con total {}", guardada.getId(),
                registradoPor, guardada.getMontoTotal());

        return OrdenResponseDto.fromEntity(guardada, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<OrdenResumenDto> buscar(EstadoOrdenEnum estado, CanalOrigenEnum canal,
            TipoOrdenEnum tipoOrden, UUID clienteId, String mesaNumero,
            ZonedDateTime desde, ZonedDateTime hasta, Pageable pageable) {
        return PageResponseDto.de(
                ordenRepository.buscar(estado, canal, tipoOrden, clienteId, mesaNumero, desde, hasta, pageable),
                OrdenResumenDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrdenResumenDto> activas() {
        return ordenRepository.findByEstadoInOrderByDateCreatedAsc(List.of(
                EstadoOrdenEnum.ENCOLADO, EstadoOrdenEnum.EN_PREPARACION,
                EstadoOrdenEnum.EN_DESPACHO, EstadoOrdenEnum.ENTREGADO))
                .stream().map(OrdenResumenDto::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrdenResponseDto obtenerPorId(UUID id) {
        return OrdenResponseDto.fromEntity(buscarCompleta(id), false);
    }

    @Override
    @Transactional
    public OrdenResponseDto agregarDetalle(UUID ordenId, ItemOrdenRequestDto item) {
        Orden orden = buscarCompleta(ordenId);
        exigirEditable(orden);

        inventarioService.descontarPorVenta(consumoDe(List.of(item)),
                "Agregado a comanda " + ordenId, trabajadorContexto.idActualONulo());

        orden.addDetalle(construirDetalle(item));
        recalcularTotales(orden);
        orden.setModifiedBy(UsuarioActual.username());
        return OrdenResponseDto.fromEntity(ordenRepository.save(orden), false);
    }

    @Override
    @Transactional
    public OrdenResponseDto actualizarDetalle(UUID ordenId, UUID detalleId, ItemOrdenRequestDto item) {
        Orden orden = buscarCompleta(ordenId);
        exigirEditable(orden);

        OrdenDetalle detalle = orden.getDetalles().stream()
                .filter(d -> d.getId().equals(detalleId))
                .findFirst()
                .orElseThrow(() -> RecursoNoEncontradoException.de("la linea de comanda", detalleId));

        // Se repone lo que consumia la linea anterior y se descuenta lo nuevo,
        // para que el stock refleje exactamente lo que quedo en la comanda.
        inventarioService.reponerPorCancelacion(consumoDeDetalle(detalle),
                "Ajuste de linea en comanda " + ordenId, trabajadorContexto.idActualONulo());
        inventarioService.descontarPorVenta(consumoDe(List.of(item)),
                "Ajuste de linea en comanda " + ordenId, trabajadorContexto.idActualONulo());

        orden.getDetalles().remove(detalle);
        orden.addDetalle(construirDetalle(item));
        recalcularTotales(orden);
        orden.setModifiedBy(UsuarioActual.username());
        return OrdenResponseDto.fromEntity(ordenRepository.save(orden), false);
    }

    @Override
    @Transactional
    public OrdenResponseDto eliminarDetalle(UUID ordenId, UUID detalleId) {
        Orden orden = buscarCompleta(ordenId);
        exigirEditable(orden);

        if (orden.getDetalles().size() <= 1) {
            throw new ConflictoException(
                    "Una comanda no puede quedarse sin platillos. Cancelala si ya no se va a preparar.");
        }

        OrdenDetalle detalle = orden.getDetalles().stream()
                .filter(d -> d.getId().equals(detalleId))
                .findFirst()
                .orElseThrow(() -> RecursoNoEncontradoException.de("la linea de comanda", detalleId));

        inventarioService.reponerPorCancelacion(consumoDeDetalle(detalle),
                "Linea retirada de comanda " + ordenId, trabajadorContexto.idActualONulo());

        orden.getDetalles().remove(detalle);
        recalcularTotales(orden);
        orden.setModifiedBy(UsuarioActual.username());
        return OrdenResponseDto.fromEntity(ordenRepository.save(orden), false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromocionResponseDto> promocionesAplicables(UUID ordenId) {
        buscarCompleta(ordenId);
        return promocionService.aplicables();
    }

    @Override
    @Transactional
    public OrdenResponseDto aplicarPromocion(UUID ordenId, AplicarPromocionRequestDto request) {
        Orden orden = buscarCompleta(ordenId);
        exigirEditable(orden);
        aplicarDescuentos(orden, request.getPromocionId(), request.getCuponCodigo());
        orden.setModifiedBy(UsuarioActual.username());
        return OrdenResponseDto.fromEntity(ordenRepository.save(orden), false);
    }

    @Override
    @Transactional
    public OrdenResponseDto cambiarEstado(UUID ordenId, CambioEstadoRequestDto request) {
        Orden orden = buscarCompleta(ordenId);

        if (request.getEstado() == EstadoOrdenEnum.CANCELADO) {
            throw new ConflictoException(
                    "Cancelar exige un motivo. Usa POST /api/v1/ordenes/{id}/cancelar.");
        }

        maquinaEstados.aplicar(orden, request.getEstado());
        liberarMesaSiCorresponde(orden);
        return OrdenResponseDto.fromEntity(ordenRepository.save(orden), false);
    }

    @Override
    @Transactional
    public OrdenResponseDto actualizarFlags(UUID ordenId, FlagsRequestDto request) {
        Orden orden = buscarCompleta(ordenId);
        ZonedDateTime ahora = ZonedDateTime.now();

        if (Boolean.TRUE.equals(request.getCierreRecepcion())
                && !Boolean.TRUE.equals(orden.getFlagCierreRecepcion())) {
            orden.setFlagCierreRecepcion(true);
            orden.setTiempoCierreRecepcion(ahora);
        }
        if (Boolean.TRUE.equals(request.getCierrePlatillo())
                && !Boolean.TRUE.equals(orden.getFlagCierrePlatillo())) {
            orden.setFlagCierrePlatillo(true);
            orden.setTiempoCierrePlatillo(ahora);
        }
        if (Boolean.TRUE.equals(request.getCierreDespacho())
                && !Boolean.TRUE.equals(orden.getFlagCierreDespacho())) {
            orden.setFlagCierreDespacho(true);
            orden.setTiempoCierreDespacho(ahora);
        }

        orden.setModifiedBy(UsuarioActual.username());
        return OrdenResponseDto.fromEntity(ordenRepository.save(orden), false);
    }

    @Override
    @Transactional
    public OrdenResponseDto cancelar(UUID ordenId, CancelarOrdenRequestDto request) {
        Orden orden = buscarCompleta(ordenId);
        maquinaEstados.aplicar(orden, EstadoOrdenEnum.CANCELADO);
        orden.setMotivoCancelacion(request.getMotivo());

        if (request.getReponerStock() == null || request.getReponerStock()) {
            Map<UUID, BigDecimal> consumo = new LinkedHashMap<>();
            for (OrdenDetalle detalle : orden.getDetalles()) {
                consumoDeDetalle(detalle).forEach((insumoId, cantidad) ->
                        consumo.merge(insumoId, cantidad, BigDecimal::add));
            }
            inventarioService.reponerPorCancelacion(consumo,
                    "Reposicion por cancelacion de comanda " + ordenId + ": " + request.getMotivo(),
                    trabajadorContexto.idActualONulo());
        }

        liberarMesaSiCorresponde(orden);
        return OrdenResponseDto.fromEntity(ordenRepository.save(orden), false);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketCocinaDto ticket(UUID ordenId) {
        Orden orden = buscarCompleta(ordenId);
        return TicketCocinaDto.builder()
                .ordenId(orden.getId())
                .correlativo(orden.getId().toString().substring(0, 8).toUpperCase())
                .tipoOrden(orden.getTipoOrden())
                .mesaNumero(orden.getMesaNumero())
                .clienteNombre(orden.getCliente() != null
                        ? (orden.getCliente().getNombres() + " " + orden.getCliente().getApellidos()).trim()
                        : "Sin identificar")
                .emitido(ZonedDateTime.now())
                .lineas(orden.getDetalles().stream()
                        .map(d -> TicketCocinaDto.LineaTicket.builder()
                                .cantidad(d.getCantidad())
                                .platillo(d.getPlatillo().getNombre())
                                .complementos(d.getComplementos().stream()
                                        .map(c -> c.getComplemento().getNombre())
                                        .toList())
                                .nota(d.getExcepcionesNota())
                                .build())
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrdenResumenDto> historialDelCliente(UUID clienteId) {
        return ordenRepository.findByClienteIdOrderByDateCreatedDesc(clienteId).stream()
                .map(OrdenResumenDto::fromEntity)
                .toList();
    }

    // ------------------------------------------------------------------
    // Apoyo
    // ------------------------------------------------------------------

    private OrdenDetalle construirDetalle(ItemOrdenRequestDto item) {
        Platillo platillo = platilloRepository.findById(item.getPlatilloId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("el platillo", item.getPlatilloId()));

        if (Boolean.FALSE.equals(platillo.getActivo())) {
            throw new ConflictoException("El platillo " + platillo.getNombre()
                    + " esta fuera de carta y no se puede pedir.");
        }

        // El precio se congela al momento de la venta: si manana sube la carta,
        // la comanda historica conserva lo que realmente se cobro.
        BigDecimal precioUnitario = platillo.getPrecioVentaBase();
        BigDecimal cantidad = BigDecimal.valueOf(item.getCantidad());
        BigDecimal subtotal = precioUnitario.multiply(cantidad);

        OrdenDetalle detalle = OrdenDetalle.builder()
                .platillo(platillo)
                .cantidad(item.getCantidad())
                .precioVentaProducto(precioUnitario)
                .excepcionesNota(item.getExcepcionesNota())
                .createdBy(UsuarioActual.username())
                .build();

        if (item.getComplementos() != null) {
            for (ComplementoItemDto complementoItem : item.getComplementos()) {
                ComplementoPlatillo complemento = complementoRepository
                        .findById(complementoItem.getComplementoId())
                        .orElseThrow(() -> RecursoNoEncontradoException.de("el complemento",
                                complementoItem.getComplementoId()));

                int cantidadComplemento = complementoItem.getCantidad() != null
                        ? complementoItem.getCantidad() : 1;

                OrdenDetalleComplemento linea = OrdenDetalleComplemento.builder()
                        .ordenDetalle(detalle)
                        .complemento(complemento)
                        .cantidad(cantidadComplemento)
                        .precioVentaComplemento(complemento.getPrecioAdicional())
                        .createdBy(UsuarioActual.username())
                        .build();

                detalle.getComplementos().add(linea);
                subtotal = subtotal.add(complemento.getPrecioAdicional()
                        .multiply(BigDecimal.valueOf(cantidadComplemento))
                        .multiply(cantidad));
            }
        }

        detalle.setMontoSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        return detalle;
    }

    private void recalcularTotales(Orden orden) {
        BigDecimal subtotal = orden.getDetalles().stream()
                .map(OrdenDetalle::getMontoSubtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal descuento = orden.getMontoDescuento() != null
                ? orden.getMontoDescuento() : BigDecimal.ZERO;

        orden.setMontoSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        orden.setMontoTotal(subtotal.subtract(descuento).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP));
    }

    private void aplicarDescuentos(Orden orden, UUID promocionId, String cuponCodigo) {
        BigDecimal subtotal = orden.getDetalles().stream()
                .map(OrdenDetalle::getMontoSubtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal descuento = BigDecimal.ZERO;

        orden.setPromocion(null);
        orden.setCuponCodigo(null);

        if (promocionId != null) {
            Promocion promocion = promocionRepository.findById(promocionId)
                    .orElseThrow(() -> RecursoNoEncontradoException.de("la promocion", promocionId));
            exigirPromocionVigente(promocion);
            descuento = descuento.add(descuentoDe(subtotal,
                    promocion.getPorcentajeDescuento(), promocion.getMontoDescuento()));
            orden.setPromocion(promocion);
        }

        if (cuponCodigo != null && !cuponCodigo.isBlank()) {
            Cupon cupon = cuponRepository.findByCodigoIgnoreCase(cuponCodigo.trim())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No existe el cupon " + cuponCodigo + "."));
            if (!cupon.estaVigente()) {
                throw new ConflictoException("El cupon " + cuponCodigo
                        + " ya fue canjeado o esta vencido.");
            }
            if (orden.getCliente() == null
                    || !cupon.getCliente().getId().equals(orden.getCliente().getId())) {
                throw new ConflictoException("El cupon pertenece a otro cliente.");
            }
            descuento = descuento.add(descuentoDe(subtotal,
                    cupon.getPorcentajeDescuento(), cupon.getMontoDescuento()));
            orden.setCuponCodigo(cupon.getCodigo());
        }

        descuento = descuento.min(subtotal).setScale(2, RoundingMode.HALF_UP);
        orden.setMontoSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        orden.setMontoDescuento(descuento);
        orden.setMontoTotal(subtotal.subtract(descuento).setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal descuentoDe(BigDecimal subtotal, BigDecimal porcentaje, BigDecimal monto) {
        BigDecimal total = BigDecimal.ZERO;
        if (porcentaje != null && porcentaje.signum() > 0) {
            total = total.add(subtotal.multiply(porcentaje)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        if (monto != null && monto.signum() > 0) {
            total = total.add(monto);
        }
        return total;
    }

    private void exigirPromocionVigente(Promocion promocion) {
        ZonedDateTime ahora = ZonedDateTime.now();
        boolean vigente = Boolean.TRUE.equals(promocion.getActiva())
                && promocion.getFechaInicio() != null && !promocion.getFechaInicio().isAfter(ahora)
                && promocion.getFechaFin() != null && !promocion.getFechaFin().isBefore(ahora);
        if (!vigente) {
            throw new ConflictoException("La promocion " + promocion.getNombre() + " no esta vigente.");
        }
        if (Boolean.TRUE.equals(promocion.getRequiereInsumoExtra())) {
            boolean hayStock = promocion.getInsumoExtra() != null
                    && promocion.getInsumoExtra().getStockActual() != null
                    && promocion.getInsumoExtra().getStockActual().signum() > 0;
            if (!hayStock) {
                throw new ConflictoException("La promocion " + promocion.getNombre()
                        + " esta agotada: no queda stock del insumo extra.");
            }
        }
    }

    private Map<UUID, BigDecimal> consumoDe(List<ItemOrdenRequestDto> items) {
        List<DisponibilidadRequestDto.ItemDisponibilidad> convertidos = items.stream()
                .map(item -> DisponibilidadRequestDto.ItemDisponibilidad.builder()
                        .platilloId(item.getPlatilloId())
                        .cantidad(item.getCantidad())
                        .complementoIds(item.getComplementos() == null ? List.of()
                                : item.getComplementos().stream()
                                        .map(ComplementoItemDto::getComplementoId)
                                        .toList())
                        .build())
                .toList();
        return inventarioService.calcularConsumo(convertidos);
    }

    private Map<UUID, BigDecimal> consumoDeDetalle(OrdenDetalle detalle) {
        return inventarioService.calcularConsumo(List.of(
                DisponibilidadRequestDto.ItemDisponibilidad.builder()
                        .platilloId(detalle.getPlatillo().getId())
                        .cantidad(detalle.getCantidad())
                        .complementoIds(detalle.getComplementos().stream()
                                .map(c -> c.getComplemento().getId())
                                .toList())
                        .build()));
    }

    private Map<String, Object> payloadFacturacion(Orden orden) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ordenId", orden.getId().toString());
        payload.put("tipoOrden", orden.getTipoOrden().name());
        payload.put("canalOrigen", orden.getCanalOrigen().name());
        payload.put("montoSubtotal", orden.getMontoSubtotal());
        payload.put("montoDescuento", orden.getMontoDescuento());
        payload.put("montoTotal", orden.getMontoTotal());
        payload.put("tipoPago", orden.getTipoPago().name());
        payload.put("fechaEmision", orden.getTiempoInicioGlobal() != null
                ? orden.getTiempoInicioGlobal().toString()
                : ZonedDateTime.now().toString());

        if (orden.getCliente() != null) {
            Map<String, Object> cliente = new LinkedHashMap<>();
            cliente.put("id", orden.getCliente().getId().toString());
            cliente.put("numeroDocumento", orden.getCliente().getDni());
            cliente.put("denominacion",
                    (orden.getCliente().getNombres() + " " + orden.getCliente().getApellidos()).trim());
            payload.put("cliente", cliente);
        }

        payload.put("detalles", orden.getDetalles().stream()
                .map(d -> {
                    Map<String, Object> linea = new LinkedHashMap<>();
                    linea.put("descripcion", d.getPlatillo().getNombre());
                    linea.put("cantidad", d.getCantidad());
                    linea.put("precioUnitario", d.getPrecioVentaProducto());
                    linea.put("montoTotal", d.getMontoSubtotal());
                    return linea;
                })
                .toList());

        return payload;
    }

    private void exigirEditable(Orden orden) {
        if (!MaquinaEstadosOrden.EDITABLES.contains(orden.getEstado())) {
            throw new ConflictoException("La comanda esta en estado " + orden.getEstado()
                    + " y ya no admite cambios de contenido.");
        }
    }

    private void liberarMesaSiCorresponde(Orden orden) {
        Mesa mesa = orden.getMesa();
        if (mesa == null) {
            return;
        }
        boolean cerrada = orden.getEstado() == EstadoOrdenEnum.CONCLUIDO
                || orden.getEstado() == EstadoOrdenEnum.CANCELADO
                || orden.getEstado() == EstadoOrdenEnum.FRAUDULENTO;
        if (cerrada) {
            mesa.setEstado(EstadoMesaEnum.LIBRE);
            mesa.setModifiedBy(UsuarioActual.username());
            mesaRepository.save(mesa);
        }
    }

    private Cliente resolverCliente(UUID clienteId) {
        if (clienteId == null) {
            return null;
        }
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el cliente", clienteId));
    }

    private void validarPorFraude(Cliente cliente) {
        if (cliente != null && Boolean.TRUE.equals(cliente.getBloqueadoPorFraude())) {
            throw new ConflictoException("El cliente esta bloqueado por fraude y no puede generar comandas. "
                    + "Un administrador debe desbloquearlo primero.");
        }
    }

    private Mesa resolverMesa(CrearOrdenRequestDto request) {
        if (request.getTipoOrden() != TipoOrdenEnum.MESA) {
            return null;
        }
        if (request.getMesaId() == null) {
            throw new IllegalArgumentException("Una comanda de mesa necesita el identificador de la mesa.");
        }
        Mesa mesa = mesaRepository.findById(request.getMesaId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("la mesa", request.getMesaId()));
        if (mesa.getEstado() == EstadoMesaEnum.INHABILITADA) {
            throw new ConflictoException("La mesa " + mesa.getNumero() + " esta inhabilitada.");
        }
        return mesa;
    }

    /**
     * Quien firma la comanda y el movimiento de stock que arrastra.
     *
     * <p>En el POS es el mozo autenticado. Un pedido que entra por el bot de
     * clientes no tiene mozo detras, y {@code controles_insumo.trabajador_id}
     * es NOT NULL: sin autor, la comanda entera se cae al descontar insumos.
     * Esos canales los firma el trabajador de sistema que representa al bot,
     * para que la salida de stock diga de donde vino en lugar de quedar
     * huerfana.
     *
     * <p>Una comanda que el mozo arma por Discord si tiene persona detras, pero
     * llega sin sesion HTTP: el bot la crea pasandole el trabajador ya resuelto
     * por {@code /vincular}, asi que aqui entra ya con autor y no cae en esta
     * rama.
     */
    private UUID resolverAutor(CanalOrigenEnum canal) {
        UUID autenticado = trabajadorContexto.idActualONulo();
        if (autenticado != null) {
            return autenticado;
        }
        if (canal == CanalOrigenEnum.DISCORD_BOT || canal == CanalOrigenEnum.WHATSAPP_BOT) {
            return trabajadorContexto.idDelBotDeClientes().orElseThrow(() -> new ConflictoException(
                    "Falta el trabajador de sistema '" + TrabajadorContexto.USERNAME_BOT_CLIENTES
                            + "' que firma las comandas del bot de clientes. "
                            + "Arranca el backend con app.seed.enabled=true para que se siembre."));
        }
        return null;
    }

    private void validarDelivery(CrearOrdenRequestDto request) {
        if (request.getTipoOrden() == TipoOrdenEnum.DELIVERY
                && (request.getDireccionDelivery() == null || request.getDireccionDelivery().isBlank())) {
            throw new IllegalArgumentException("Una comanda de delivery necesita direccion de entrega.");
        }
    }

    private String generarOtp() {
        return String.format("%06d", ALEATORIO.nextInt(1_000_000));
    }

    private Orden buscarCompleta(UUID id) {
        return ordenRepository.findCompletaById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la comanda", id));
    }
}
