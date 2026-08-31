package com.chaquena.backend_logistica.pagos.service.impl;

import com.chaquena.backend_logistica.auth.service.TrabajadorContexto;
import com.chaquena.backend_logistica.clientes.domain.Cliente;
import com.chaquena.backend_logistica.clientes.repository.ClienteRepository;
import com.chaquena.backend_logistica.pagos.domain.EstadoPagoEnum;
import com.chaquena.backend_logistica.pagos.domain.Pago;
import com.chaquena.backend_logistica.pagos.dto.*;
import com.chaquena.backend_logistica.pagos.repository.PagoRepository;
import com.chaquena.backend_logistica.pagos.service.PagoService;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.pedidos.domain.TipoPagoEnum;
import com.chaquena.backend_logistica.pedidos.dto.OrdenResponseDto;
import com.chaquena.backend_logistica.pedidos.repository.OrdenRepository;
import com.chaquena.backend_logistica.pedidos.service.MaquinaEstadosOrden;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PagoServiceImpl implements PagoService {

    private static final int PUNTOS_RIESGO_POR_DEFECTO = 50;

    private final PagoRepository pagoRepository;
    private final OrdenRepository ordenRepository;
    private final ClienteRepository clienteRepository;
    private final MaquinaEstadosOrden maquinaEstados;
    private final TrabajadorContexto trabajadorContexto;

    /**
     * Registra un cobro. El efectivo se confirma en el acto y devuelve el
     * vuelto calculado; billetera digital y tarjeta quedan PENDIENTE hasta que
     * el cajero verifique la acreditacion, que es la segunda capa del diseno
     * anti-fraude.
     */
    @Override
    @Transactional
    public PagoResponseDto registrar(UUID ordenId, RegistrarPagoRequestDto request) {
        Orden orden = buscarOrden(ordenId);

        if (orden.getEstado() == EstadoOrdenEnum.CANCELADO
                || orden.getEstado() == EstadoOrdenEnum.FRAUDULENTO) {
            throw new ConflictoException("La comanda esta en estado " + orden.getEstado()
                    + " y no admite cobros.");
        }

        BigDecimal monto = request.getMonto().setScale(2, RoundingMode.HALF_UP);
        BigDecimal vuelto = null;
        BigDecimal entregado = request.getMontoEntregado();

        if (request.getTipoPago() == TipoPagoEnum.EFECTIVO) {
            if (entregado == null) {
                entregado = monto;
            }
            if (entregado.compareTo(monto) < 0) {
                throw new IllegalArgumentException(
                        "El monto entregado (" + entregado.toPlainString()
                                + ") es menor que el cobro (" + monto.toPlainString() + ").");
            }
            vuelto = entregado.subtract(monto).setScale(2, RoundingMode.HALF_UP);
        } else if (request.getReferencia() == null || request.getReferencia().isBlank()) {
            throw new IllegalArgumentException(
                    "Los pagos con billetera digital o tarjeta necesitan el codigo de operacion "
                            + "o el numero de voucher.");
        }

        boolean confirmado = request.getTipoPago() == TipoPagoEnum.EFECTIVO
                || Boolean.TRUE.equals(request.getConfirmado());

        Pago pago = Pago.builder()
                .orden(orden)
                .tipoPago(request.getTipoPago())
                .monto(monto)
                .montoEntregado(entregado)
                .vuelto(vuelto)
                .referencia(request.getReferencia())
                .estado(confirmado ? EstadoPagoEnum.CONFIRMADO : EstadoPagoEnum.PENDIENTE)
                .esFraudulento(false)
                .observacion(request.getObservacion())
                .cajeroId(trabajadorContexto.idActualONulo())
                .createdBy(UsuarioActual.username())
                .build();

        Pago guardado = pagoRepository.save(pago);
        cerrarOrdenSiEstaPagada(orden);

        return PagoResponseDto.fromEntity(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PagoResponseDto> pagosDeOrden(UUID ordenId) {
        buscarOrden(ordenId);
        return pagoRepository.findByOrdenIdOrderByDateCreatedAsc(ordenId).stream()
                .map(PagoResponseDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public PagoResponseDto confirmar(UUID pagoId) {
        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el pago", pagoId));

        if (pago.getEstado() == EstadoPagoEnum.CONFIRMADO) {
            throw new ConflictoException("El pago ya estaba confirmado.");
        }
        if (pago.getEstado() == EstadoPagoEnum.FRAUDULENTO) {
            throw new ConflictoException("El pago esta marcado como fraudulento y no se puede confirmar.");
        }

        pago.setEstado(EstadoPagoEnum.CONFIRMADO);
        pago.setModifiedBy(UsuarioActual.username());
        Pago guardado = pagoRepository.save(pago);

        cerrarOrdenSiEstaPagada(pago.getOrden());
        return PagoResponseDto.fromEntity(guardado);
    }

    /**
     * Marca la comanda como fraudulenta, sube el score de riesgo del cliente y
     * deja constancia en el log de auditoria para el panel de administracion.
     */
    @Override
    @Transactional
    public OrdenResponseDto alertaFraude(UUID ordenId, AlertaFraudeRequestDto request) {
        Orden orden = buscarOrden(ordenId);
        maquinaEstados.aplicar(orden, EstadoOrdenEnum.FRAUDULENTO);
        orden.setMotivoCancelacion(request.getMotivo());

        pagoRepository.findByOrdenIdOrderByDateCreatedAsc(ordenId).forEach(pago -> {
            pago.setEsFraudulento(true);
            pago.setEstado(EstadoPagoEnum.FRAUDULENTO);
            pago.setObservacion(request.getMotivo());
            pago.setModifiedBy(UsuarioActual.username());
            pagoRepository.save(pago);
        });

        Cliente cliente = orden.getCliente();
        if (cliente != null) {
            int puntos = request.getPuntosRiesgo() != null
                    ? request.getPuntosRiesgo()
                    : PUNTOS_RIESGO_POR_DEFECTO;
            int score = (cliente.getScoreFraude() != null ? cliente.getScoreFraude() : 0) + puntos;
            cliente.setScoreFraude(score);
            if (Boolean.TRUE.equals(request.getBloquearCliente()) || score >= 100) {
                cliente.setBloqueadoPorFraude(true);
            }
            cliente.setModifiedBy(UsuarioActual.username());
            clienteRepository.save(cliente);
        }

        log.warn("ALERTA DE FRAUDE - comanda {} - reporta {}: {}",
                ordenId, UsuarioActual.username(), request.getMotivo());

        return OrdenResponseDto.fromEntity(ordenRepository.save(orden), false);
    }

    @Override
    @Transactional(readOnly = true)
    public ArqueoCajaDto arqueo(ZonedDateTime desde, ZonedDateTime hasta) {
        List<ArqueoCajaDto.PorMetodo> porMetodo =
                pagoRepository.arqueoPorMetodo(EstadoPagoEnum.CONFIRMADO, desde, hasta).stream()
                        .map(fila -> ArqueoCajaDto.PorMetodo.builder()
                                .tipoPago((TipoPagoEnum) fila[0])
                                .cantidad(((Number) fila[1]).longValue())
                                .total((BigDecimal) fila[2])
                                .build())
                        .toList();

        BigDecimal total = porMetodo.stream()
                .map(ArqueoCajaDto.PorMetodo::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long cantidad = porMetodo.stream().mapToLong(ArqueoCajaDto.PorMetodo::getCantidad).sum();

        return ArqueoCajaDto.builder()
                .desde(desde)
                .hasta(hasta)
                .totalCobrado(total)
                .cantidadPagos(cantidad)
                .porMetodo(porMetodo)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PagoResponseDto> alertasFraude() {
        return pagoRepository.findByEsFraudulentoTrueOrderByDateCreatedDesc().stream()
                .map(PagoResponseDto::fromEntity)
                .toList();
    }

    /**
     * Cuando lo confirmado alcanza el total de la comanda, la orden pasa a
     * PAGADO. Solo aplica si ya fue entregada: no se cierra una comanda que
     * todavia esta en cocina.
     */
    private void cerrarOrdenSiEstaPagada(Orden orden) {
        if (orden.getEstado() != EstadoOrdenEnum.ENTREGADO) {
            return;
        }
        BigDecimal confirmado = pagoRepository.totalConfirmadoDeOrden(
                orden.getId(), EstadoPagoEnum.CONFIRMADO);
        if (confirmado != null && confirmado.compareTo(orden.getMontoTotal()) >= 0) {
            maquinaEstados.aplicar(orden, EstadoOrdenEnum.PAGADO);
            ordenRepository.save(orden);
        }
    }

    private Orden buscarOrden(UUID ordenId) {
        return ordenRepository.findCompletaById(ordenId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la comanda", ordenId));
    }
}
