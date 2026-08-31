package com.chaquena.backend_logistica.fidelizacion.service.impl;

import com.chaquena.backend_logistica.clientes.domain.Cliente;
import com.chaquena.backend_logistica.clientes.repository.ClienteRepository;
import com.chaquena.backend_logistica.fidelizacion.domain.ConfiguracionLocal;
import com.chaquena.backend_logistica.fidelizacion.domain.Cupon;
import com.chaquena.backend_logistica.fidelizacion.domain.EstadoCuponEnum;
import com.chaquena.backend_logistica.fidelizacion.dto.*;
import com.chaquena.backend_logistica.fidelizacion.repository.CuponRepository;
import com.chaquena.backend_logistica.fidelizacion.service.ConfiguracionService;
import com.chaquena.backend_logistica.fidelizacion.service.FidelizacionService;
import com.chaquena.backend_logistica.pedidos.domain.CalificacionFeedback;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.pedidos.repository.CalificacionFeedbackRepository;
import com.chaquena.backend_logistica.pedidos.repository.OrdenRepository;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FidelizacionServiceImpl implements FidelizacionService {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final CalificacionFeedbackRepository feedbackRepository;
    private final OrdenRepository ordenRepository;
    private final ClienteRepository clienteRepository;
    private final CuponRepository cuponRepository;
    private final ConfiguracionService configuracionService;

    /**
     * Registra la calificacion y, si con esta el cliente alcanza el umbral N
     * que define administracion, emite el cupon de incentivo en el acto.
     */
    @Override
    @Transactional
    public FeedbackResponseDto registrarFeedback(UUID ordenId, FeedbackRequestDto request) {
        Orden orden = ordenRepository.findCompletaById(ordenId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la comanda", ordenId));

        if (feedbackRepository.existsByOrdenId(ordenId)) {
            throw new ConflictoException("La comanda ya fue calificada.");
        }

        CalificacionFeedback feedback = CalificacionFeedback.builder()
                .orden(orden)
                .cliente(orden.getCliente())
                .puntajeAtencion(request.getPuntajeAtencion())
                .puntajeComida(request.getPuntajeComida())
                .puntajeLugar(request.getPuntajeLugar())
                .comentario(request.getComentario())
                .createdBy(UsuarioActual.username())
                .build();

        FeedbackResponseDto respuesta = FeedbackResponseDto
                .fromEntity(feedbackRepository.save(feedback));

        if (orden.getCliente() == null) {
            respuesta.setMensajeFidelizacion(
                    "Gracias por tu calificacion. Identificate en tu proxima visita para acumular premios.");
            return respuesta;
        }

        Cliente cliente = orden.getCliente();
        ConfiguracionLocal config = configuracionService.obtener();
        int requeridas = config.getCalificacionesParaCupon() != null
                ? config.getCalificacionesParaCupon() : 5;
        long realizadas = feedbackRepository.countByClienteId(cliente.getId());

        cliente.setPuntosFidelidad((cliente.getPuntosFidelidad() != null
                ? cliente.getPuntosFidelidad() : 0) + 1);
        cliente.setModifiedBy(UsuarioActual.username());
        clienteRepository.save(cliente);

        if (realizadas > 0 && realizadas % requeridas == 0) {
            Cupon cupon = emitirCupon(cliente, config);
            respuesta.setCuponGenerado(CuponResponseDto.fromEntity(cupon));
            respuesta.setMensajeFidelizacion("Llegaste a " + realizadas
                    + " calificaciones. Te ganaste el cupon " + cupon.getCodigo()
                    + " para tu proxima visita.");
        } else {
            long faltan = requeridas - (realizadas % requeridas);
            respuesta.setMensajeFidelizacion("Gracias por calificar. Te faltan " + faltan
                    + (faltan == 1 ? " calificacion" : " calificaciones") + " para tu proximo premio.");
        }

        return respuesta;
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackResponseDto feedbackDeOrden(UUID ordenId) {
        return feedbackRepository.findByOrdenId(ordenId)
                .map(FeedbackResponseDto::fromEntity)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La comanda todavia no tiene calificacion."));
    }

    @Override
    @Transactional(readOnly = true)
    public FidelizacionDto progresoDelCliente(UUID clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el cliente", clienteId));

        int requeridas = configuracionService.calificacionesParaCupon();
        long realizadas = feedbackRepository.countByClienteId(clienteId);
        long faltan = requeridas - (realizadas % requeridas);
        if (faltan == requeridas && realizadas > 0) {
            faltan = 0;
        }

        long vigentes = cuponRepository.findByClienteIdAndEstado(clienteId, EstadoCuponEnum.VIGENTE)
                .stream().filter(Cupon::estaVigente).count();

        return FidelizacionDto.builder()
                .clienteId(clienteId)
                .calificacionesRealizadas(realizadas)
                .calificacionesRequeridas(requeridas)
                .calificacionesFaltantes(faltan)
                .cuponesVigentes(vigentes)
                .puntosFidelidad(cliente.getPuntosFidelidad())
                .mensaje(faltan == 0
                        ? "Tienes premio disponible."
                        : "Te faltan " + faltan + " calificaciones para tu proximo premio.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuponResponseDto> cuponesDelCliente(UUID clienteId) {
        return cuponRepository.findByClienteIdOrderByFechaEmisionDesc(clienteId).stream()
                .map(CuponResponseDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public CuponResponseDto canjear(String codigo, UUID ordenId) {
        Cupon cupon = cuponRepository.findByCodigoIgnoreCase(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cupon " + codigo + "."));

        if (cupon.getEstado() == EstadoCuponEnum.CANJEADO) {
            throw new ConflictoException("El cupon " + codigo + " ya fue canjeado.");
        }
        if (!cupon.estaVigente()) {
            cupon.setEstado(EstadoCuponEnum.VENCIDO);
            cuponRepository.save(cupon);
            throw new ConflictoException("El cupon " + codigo + " esta vencido.");
        }

        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la comanda", ordenId));
        if (orden.getCliente() == null
                || !orden.getCliente().getId().equals(cupon.getCliente().getId())) {
            throw new ConflictoException("El cupon pertenece a otro cliente.");
        }

        cupon.setEstado(EstadoCuponEnum.CANJEADO);
        cupon.setOrdenCanjeId(ordenId);
        cupon.setFechaCanje(ZonedDateTime.now());
        cupon.setModifiedBy(UsuarioActual.username());

        return CuponResponseDto.fromEntity(cuponRepository.save(cupon));
    }

    @Override
    @Transactional(readOnly = true)
    public ReporteSatisfaccionDto satisfaccion(ZonedDateTime desde, ZonedDateTime hasta) {
        Object[] fila = feedbackRepository.promedios(desde, hasta);

        // La consulta devuelve una tupla; segun el driver puede venir anidada.
        Object[] datos = (fila != null && fila.length == 1 && fila[0] instanceof Object[] anidada)
                ? anidada
                : fila;

        double atencion = datos != null && datos.length > 0 ? ((Number) datos[0]).doubleValue() : 0;
        double comida = datos != null && datos.length > 1 ? ((Number) datos[1]).doubleValue() : 0;
        double lugar = datos != null && datos.length > 2 ? ((Number) datos[2]).doubleValue() : 0;
        long total = datos != null && datos.length > 3 ? ((Number) datos[3]).longValue() : 0;

        return ReporteSatisfaccionDto.builder()
                .desde(desde)
                .hasta(hasta)
                .totalCalificaciones(total)
                .promedioAtencion(redondear(atencion))
                .promedioComida(redondear(comida))
                .promedioLugar(redondear(lugar))
                .promedioGeneral(redondear((atencion + comida + lugar) / 3.0))
                .build();
    }

    private Cupon emitirCupon(Cliente cliente, ConfiguracionLocal config) {
        int dias = config.getDiasVigenciaCupon() != null ? config.getDiasVigenciaCupon() : 30;

        Cupon cupon = Cupon.builder()
                .codigo(generarCodigoUnico())
                .cliente(cliente)
                .descripcion("Premio por " + config.getCalificacionesParaCupon() + " calificaciones")
                .porcentajeDescuento(config.getPorcentajeDescuentoCupon())
                .fechaEmision(ZonedDateTime.now())
                .fechaVencimiento(ZonedDateTime.now().plusDays(dias))
                .estado(EstadoCuponEnum.VIGENTE)
                .createdBy(UsuarioActual.username())
                .build();

        return cuponRepository.save(cupon);
    }

    /** Alfabeto sin caracteres ambiguos: el codigo se dicta por telefono. */
    private String generarCodigoUnico() {
        for (int intento = 0; intento < 20; intento++) {
            StringBuilder codigo = new StringBuilder("CHQ-");
            for (int i = 0; i < 6; i++) {
                codigo.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
            }
            String candidato = codigo.toString();
            if (!cuponRepository.existsByCodigoIgnoreCase(candidato)) {
                return candidato;
            }
        }
        throw new ConflictoException("No se pudo generar un codigo de cupon unico. Intenta de nuevo.");
    }

    private Double redondear(double valor) {
        return Math.round(valor * 100) / 100.0;
    }
}
