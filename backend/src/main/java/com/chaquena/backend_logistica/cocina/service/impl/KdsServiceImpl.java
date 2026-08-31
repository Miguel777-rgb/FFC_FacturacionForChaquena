package com.chaquena.backend_logistica.cocina.service.impl;

import com.chaquena.backend_logistica.cocina.dto.ComandaKdsDto;
import com.chaquena.backend_logistica.cocina.dto.KpisCocinaDto;
import com.chaquena.backend_logistica.cocina.dto.ReportarFaltanteRequestDto;
import com.chaquena.backend_logistica.cocina.service.KdsService;
import com.chaquena.backend_logistica.fidelizacion.service.ConfiguracionService;
import com.chaquena.backend_logistica.inventario.domain.Insumo;
import com.chaquena.backend_logistica.inventario.repository.InsumoRepository;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.pedidos.domain.OrdenDetalle;
import com.chaquena.backend_logistica.pedidos.dto.OrdenResponseDto;
import com.chaquena.backend_logistica.pedidos.repository.OrdenDetalleRepository;
import com.chaquena.backend_logistica.pedidos.repository.OrdenRepository;
import com.chaquena.backend_logistica.pedidos.service.MaquinaEstadosOrden;
import com.chaquena.backend_logistica.shared.dto.MensajeDto;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KdsServiceImpl implements KdsService {

    private final OrdenRepository ordenRepository;
    private final OrdenDetalleRepository ordenDetalleRepository;
    private final InsumoRepository insumoRepository;
    private final MaquinaEstadosOrden maquinaEstados;
    private final ConfiguracionService configuracionService;

    /**
     * Cola de cocina por orden de llegada. Se resuelve con polling desde el
     * frontend: para el volumen de un local basta y evita montar SSE.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ComandaKdsDto> cola() {
        int objetivo = configuracionService.minutosObjetivoCocina();

        return ordenRepository.findByEstadoInOrderByDateCreatedAsc(
                List.of(EstadoOrdenEnum.ENCOLADO, EstadoOrdenEnum.EN_PREPARACION)).stream()
                .map(orden -> {
                    Orden completa = ordenRepository.findCompletaById(orden.getId()).orElse(orden);
                    ZonedDateTime recibida = completa.getTiempoInicioGlobal() != null
                            ? completa.getTiempoInicioGlobal()
                            : completa.getDateCreated();
                    long minutos = recibida != null
                            ? Duration.between(recibida, ZonedDateTime.now()).toMinutes()
                            : 0L;

                    return ComandaKdsDto.builder()
                            .ordenId(completa.getId())
                            .correlativo(completa.getId().toString().substring(0, 8).toUpperCase())
                            .tipoOrden(completa.getTipoOrden())
                            .estado(completa.getEstado())
                            .mesaNumero(completa.getMesaNumero())
                            .recibida(recibida)
                            .minutosEnCola(minutos)
                            .tiempoEstimadoCocinaMinutos(completa.getTiempoEstimadoCocinaMinutos())
                            .fueraDeObjetivo(minutos > objetivo)
                            .flagCierrePlatillo(completa.getFlagCierrePlatillo())
                            .lineas(completa.getDetalles().stream()
                                    .map(d -> ComandaKdsDto.LineaKds.builder()
                                            .detalleId(d.getId())
                                            .cantidad(d.getCantidad())
                                            .platillo(d.getPlatillo().getNombre())
                                            .complementos(d.getComplementos().stream()
                                                    .map(c -> c.getComplemento().getNombre())
                                                    .toList())
                                            .nota(d.getExcepcionesNota())
                                            .build())
                                    .toList())
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public OrdenResponseDto tomar(UUID ordenId) {
        Orden orden = buscar(ordenId);
        maquinaEstados.aplicar(orden, EstadoOrdenEnum.EN_PREPARACION);
        return OrdenResponseDto.fromEntity(ordenRepository.save(orden), false);
    }

    /**
     * Comanda lista. No cambia de estado por si sola: levanta el flag de cierre
     * de platillo y deja que mozo o despacho decidan el siguiente paso, que
     * depende de si es mesa, retiro o delivery.
     */
    @Override
    @Transactional
    public OrdenResponseDto estimarTiempo(UUID ordenId, int minutos) {
        if (minutos <= 0 || minutos > 240) {
            throw new IllegalArgumentException(
                    "El tiempo estimado debe estar entre 1 y 240 minutos, y llego " + minutos + ".");
        }
        Orden orden = buscar(ordenId);
        orden.setTiempoEstimadoCocinaMinutos(minutos);
        orden.setModifiedBy(UsuarioActual.username());
        return OrdenResponseDto.fromEntity(ordenRepository.save(orden), false);
    }

    @Override
    @Transactional
    public OrdenResponseDto marcarListo(UUID ordenId) {
        Orden orden = buscar(ordenId);
        if (!Boolean.TRUE.equals(orden.getFlagCierrePlatillo())) {
            orden.setFlagCierrePlatillo(true);
            orden.setTiempoCierrePlatillo(ZonedDateTime.now());
            orden.setModifiedBy(UsuarioActual.username());
        }
        return OrdenResponseDto.fromEntity(ordenRepository.save(orden), false);
    }

    @Override
    @Transactional
    public MensajeDto marcarDetalleListo(UUID detalleId) {
        OrdenDetalle detalle = ordenDetalleRepository.findById(detalleId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la linea de comanda", detalleId));
        detalle.setModifiedBy(UsuarioActual.username());
        ordenDetalleRepository.save(detalle);
        return MensajeDto.de("Platillo " + detalle.getPlatillo().getNombre() + " marcado como listo.");
    }

    @Override
    @Transactional(readOnly = true)
    public MensajeDto reportarFaltante(UUID ordenId, ReportarFaltanteRequestDto request) {
        Orden orden = buscar(ordenId);
        Insumo insumo = insumoRepository.findById(request.getInsumoId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("el insumo", request.getInsumoId()));

        log.warn("ALERTA DE FALTANTE - comanda {} - insumo {} (stock {}) - reporta {}: {}",
                ordenId, insumo.getNombre(), insumo.getStockActual(),
                UsuarioActual.username(), request.getDetalle());

        return MensajeDto.de("Alerta enviada al mozo de la comanda "
                + orden.getId().toString().substring(0, 8).toUpperCase()
                + ": falta " + insumo.getNombre() + ". " + request.getDetalle());
    }

    @Override
    @Transactional(readOnly = true)
    public KpisCocinaDto kpis() {
        int objetivo = configuracionService.minutosObjetivoCocina();

        List<Orden> cerradas = ordenRepository.findByEstadoInOrderByDateCreatedAsc(
                List.of(EstadoOrdenEnum.ENTREGADO, EstadoOrdenEnum.PAGADO, EstadoOrdenEnum.CONCLUIDO));

        long enCola = ordenRepository.findByEstadoInOrderByDateCreatedAsc(
                List.of(EstadoOrdenEnum.ENCOLADO)).size();
        long enPreparacion = ordenRepository.findByEstadoInOrderByDateCreatedAsc(
                List.of(EstadoOrdenEnum.EN_PREPARACION)).size();

        long fueraDeObjetivo = cerradas.stream()
                .filter(o -> minutos(o.getTiempoInicioCocina(), o.getTiempoCierrePlatillo()) != null)
                .filter(o -> minutos(o.getTiempoInicioCocina(), o.getTiempoCierrePlatillo()) > objetivo)
                .count();

        return KpisCocinaDto.builder()
                .comandasEnCola(enCola)
                .comandasEnPreparacion(enPreparacion)
                .minutosPromedioRecepcion(promedio(cerradas, EtapaCronometro.RECEPCION))
                .minutosPromedioCocina(promedio(cerradas, EtapaCronometro.COCINA))
                .minutosPromedioDespacho(promedio(cerradas, EtapaCronometro.DESPACHO))
                .minutosPromedioTotal(promedio(cerradas, EtapaCronometro.TOTAL))
                .comandasFueraDeObjetivo(fueraDeObjetivo)
                .minutosObjetivo(objetivo)
                .build();
    }

    private enum EtapaCronometro { RECEPCION, COCINA, DESPACHO, TOTAL }

    private Double promedio(List<Orden> ordenes, EtapaCronometro etapa) {
        return ordenes.stream()
                .map(o -> switch (etapa) {
                    case RECEPCION -> minutos(o.getTiempoInicioGlobal(), o.getTiempoCierreRecepcion());
                    case COCINA -> minutos(o.getTiempoInicioCocina(), o.getTiempoCierrePlatillo());
                    case DESPACHO -> minutos(o.getTiempoCierrePlatillo(), o.getTiempoCierreDespacho());
                    case TOTAL -> minutos(o.getTiempoInicioGlobal(), o.getTiempoCierreDespacho());
                })
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .stream().boxed().findFirst().orElse(null);
    }

    private Long minutos(ZonedDateTime inicio, ZonedDateTime fin) {
        if (inicio == null || fin == null) {
            return null;
        }
        return Duration.between(inicio, fin).toMinutes();
    }

    private Orden buscar(UUID id) {
        return ordenRepository.findCompletaById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la comanda", id));
    }
}
