package com.chaquena.backend_logistica.reportes.service.impl;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.fidelizacion.service.ConfiguracionService;
import com.chaquena.backend_logistica.inventario.repository.InsumoRepository;
import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.repository.MesaRepository;
import com.chaquena.backend_logistica.outbox.domain.EstadoOutboxEnum;
import com.chaquena.backend_logistica.outbox.repository.OutboxEventRepository;
import com.chaquena.backend_logistica.pagos.domain.EstadoPagoEnum;
import com.chaquena.backend_logistica.pagos.repository.PagoRepository;
import com.chaquena.backend_logistica.pedidos.domain.CanalOrigenEnum;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.repository.OrdenDetalleRepository;
import com.chaquena.backend_logistica.pedidos.repository.OrdenRepository;
import com.chaquena.backend_logistica.reportes.dto.GranularidadEnum;
import com.chaquena.backend_logistica.reportes.dto.ProductoTopDto;
import com.chaquena.backend_logistica.reportes.dto.ReporteVentasDto;
import com.chaquena.backend_logistica.reportes.dto.SerieVentasDto;
import com.chaquena.backend_logistica.reportes.dto.TableroDto;
import com.chaquena.backend_logistica.reportes.dto.VentasPorMozoDto;
import com.chaquena.backend_logistica.reportes.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReporteServiceImpl implements ReporteService {

    /**
     * Una comanda solo cuenta como venta cuando llego al comensal. Las abiertas
     * todavia se pueden cancelar, asi que ni el total ni el desglose por canal
     * las miran; las canceladas y las fraudulentas quedan fuera por lo mismo.
     */
    private static final List<EstadoOrdenEnum> VENTA_EFECTIVA = List.of(
            EstadoOrdenEnum.ENTREGADO, EstadoOrdenEnum.PAGADO, EstadoOrdenEnum.CONCLUIDO);

    /** Lo que sigue en marcha: ni cerrado ni descartado. */
    private static final List<EstadoOrdenEnum> EN_CURSO = List.of(
            EstadoOrdenEnum.ENCOLADO, EstadoOrdenEnum.EN_PREPARACION, EstadoOrdenEnum.EN_DESPACHO);

    /** Tope de bloques de una serie: un ano de dias o algo mas de un mes de horas. */
    private static final int MAX_PUNTOS_SERIE = 800;

    private final OrdenRepository ordenRepository;
    private final OrdenDetalleRepository ordenDetalleRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final MesaRepository mesaRepository;
    private final InsumoRepository insumoRepository;
    private final PagoRepository pagoRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ConfiguracionService configuracionService;

    @Override
    @Transactional(readOnly = true)
    public ReporteVentasDto ventas(ZonedDateTime desde, ZonedDateTime hasta) {
        BigDecimal total = ordenRepository.totalVendido(VENTA_EFECTIVA, desde, hasta);

        List<ReporteVentasDto.PorCanal> porCanal =
                ordenRepository.ventasPorCanal(desde, hasta, VENTA_EFECTIVA).stream()
                        .map(fila -> ReporteVentasDto.PorCanal.builder()
                                .canal((CanalOrigenEnum) fila[0])
                                .cantidadComandas(((Number) fila[1]).longValue())
                                .total((BigDecimal) fila[2])
                                .build())
                        .toList();

        return ReporteVentasDto.builder()
                .desde(desde)
                .hasta(hasta)
                .totalVendido(total != null ? total : BigDecimal.ZERO)
                .porCanal(porCanal)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoTopDto> productosTop(ZonedDateTime desde, ZonedDateTime hasta, int limite) {
        return ordenDetalleRepository.platillosMasVendidos(desde, hasta, VENTA_EFECTIVA).stream()
                .limit(limite)
                .map(fila -> ProductoTopDto.builder()
                        .platillo((String) fila[0])
                        .unidadesVendidas(((Number) fila[1]).longValue())
                        .montoTotal((BigDecimal) fila[2])
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TableroDto tablero(ZonedDateTime desde, ZonedDateTime hasta) {
        Map<EstadoOrdenEnum, TableroDto.PorEstado> porEstado = new EnumMap<>(EstadoOrdenEnum.class);
        for (Object[] fila : ordenRepository.comandasPorEstado(desde, hasta)) {
            EstadoOrdenEnum estado = (EstadoOrdenEnum) fila[0];
            porEstado.put(estado, TableroDto.PorEstado.builder()
                    .estado(estado)
                    .comandas(((Number) fila[1]).longValue())
                    .total((BigDecimal) fila[2])
                    .build());
        }

        long comandasVendidas = VENTA_EFECTIVA.stream().mapToLong(e -> comandasDe(porEstado, e)).sum();
        BigDecimal totalVendido = ordenRepository.totalVendido(VENTA_EFECTIVA, desde, hasta);
        totalVendido = totalVendido != null ? totalVendido : BigDecimal.ZERO;

        return TableroDto.builder()
                .desde(desde)
                .hasta(hasta)
                .ventas(TableroDto.Ventas.builder()
                        .total(totalVendido)
                        .comandas(comandasVendidas)
                        .ticketPromedio(promedioPorComanda(totalVendido, comandasVendidas))
                        .build())
                .operacion(TableroDto.Operacion.builder()
                        .comandasCanceladas(comandasDe(porEstado, EstadoOrdenEnum.CANCELADO))
                        .comandasFraudulentas(comandasDe(porEstado, EstadoOrdenEnum.FRAUDULENTO))
                        .minutosPromedioCocina(minutosPromedioCocina(desde, hasta))
                        .minutosObjetivoCocina(configuracionService.minutosObjetivoCocina())
                        .build())
                .ahoraMismo(TableroDto.AhoraMismo.builder()
                        .comandasAbiertas(ordenRepository.countByEstadoIn(EN_CURSO))
                        .mesasOcupadas(mesaRepository.countByEstado(EstadoMesaEnum.OCUPADA))
                        .mesasActivas(mesaRepository.countByActivaTrue())
                        .insumosBajoMinimo(insumoRepository.contarBajoMinimo())
                        .pagosPorAcreditar(pagoRepository.countByEstado(EstadoPagoEnum.PENDIENTE))
                        .alertasDeFraude(pagoRepository.countByEsFraudulentoTrue())
                        .eventosOutboxPendientes(outboxEventRepository.countByStatus(EstadoOutboxEnum.PENDIENTE))
                        .eventosOutboxEnError(outboxEventRepository.countByStatus(EstadoOutboxEnum.ERROR)
                                + outboxEventRepository.countByStatus(EstadoOutboxEnum.DEAD_LETTER))
                        .build())
                .porEstado(List.copyOf(porEstado.values()))
                .build();
    }

    /**
     * Reparte las ventas del rango en bloques consecutivos.
     *
     * <p>Los bloques se recorren en la zona horaria con la que llego
     * {@code desde}, no en la del servidor: pedido desde Lima, la ultima hora de
     * un dia es la ultima hora en Lima. Y se emiten todos, tambien los vacios,
     * porque el hueco es informacion.
     */
    @Override
    @Transactional(readOnly = true)
    public SerieVentasDto serieVentas(ZonedDateTime desde, ZonedDateTime hasta, GranularidadEnum granularidad) {
        GranularidadEnum grano = granularidad != null ? granularidad : GranularidadEnum.DIA;
        ChronoUnit unidad = grano == GranularidadEnum.HORA ? ChronoUnit.HOURS : ChronoUnit.DAYS;

        Map<ZonedDateTime, long[]> comandasPorBloque = new HashMap<>();
        Map<ZonedDateTime, BigDecimal> totalPorBloque = new HashMap<>();

        for (Object[] fila : ordenRepository.ventasCrudas(desde, hasta, VENTA_EFECTIVA)) {
            ZonedDateTime bloque = truncar(((ZonedDateTime) fila[0]).withZoneSameInstant(desde.getZone()), unidad);
            comandasPorBloque.computeIfAbsent(bloque, k -> new long[1])[0]++;
            totalPorBloque.merge(bloque, (BigDecimal) fila[1], BigDecimal::add);
        }

        List<SerieVentasDto.Punto> puntos = new ArrayList<>();
        ZonedDateTime cursor = truncar(desde, unidad);
        ZonedDateTime fin = truncar(hasta, unidad);
        while (!cursor.isAfter(fin) && puntos.size() < MAX_PUNTOS_SERIE) {
            long[] cuenta = comandasPorBloque.get(cursor);
            puntos.add(SerieVentasDto.Punto.builder()
                    .inicio(cursor)
                    .comandas(cuenta == null ? 0L : cuenta[0])
                    .total(totalPorBloque.getOrDefault(cursor, BigDecimal.ZERO))
                    .build());
            cursor = cursor.plus(1, unidad);
        }

        return SerieVentasDto.builder()
                .desde(desde)
                .hasta(hasta)
                .granularidad(grano)
                .puntos(puntos)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VentasPorMozoDto> ventasPorMozo(ZonedDateTime desde, ZonedDateTime hasta) {
        List<Object[]> filas = ordenRepository.ventasPorMozo(desde, hasta, VENTA_EFECTIVA);

        List<UUID> ids = filas.stream().map(f -> (UUID) f[0]).filter(Objects::nonNull).toList();
        Map<UUID, String> nombres = new LinkedHashMap<>();
        trabajadorRepository.findAllById(ids).forEach(t -> nombres.put(t.getId(), nombreDe(t)));

        return filas.stream()
                .map(fila -> {
                    UUID mozoId = (UUID) fila[0];
                    long comandas = ((Number) fila[1]).longValue();
                    BigDecimal total = (BigDecimal) fila[2];
                    return VentasPorMozoDto.builder()
                            .mozoId(mozoId)
                            .mozo(nombres.get(mozoId))
                            .comandas(comandas)
                            .total(total)
                            .ticketPromedio(promedioPorComanda(total, comandas))
                            .build();
                })
                .toList();
    }

    // --- apoyos ---------------------------------------------------------------

    private long comandasDe(Map<EstadoOrdenEnum, TableroDto.PorEstado> porEstado, EstadoOrdenEnum estado) {
        TableroDto.PorEstado fila = porEstado.get(estado);
        return fila == null ? 0L : fila.getComandas();
    }

    private BigDecimal promedioPorComanda(BigDecimal total, long comandas) {
        if (comandas == 0 || total == null) {
            return BigDecimal.ZERO;
        }
        return total.divide(BigDecimal.valueOf(comandas), 2, RoundingMode.HALF_UP);
    }

    private Double minutosPromedioCocina(ZonedDateTime desde, ZonedDateTime hasta) {
        return ordenRepository.tiemposDeCocina(desde, hasta).stream()
                .mapToLong(fila -> Duration.between((ZonedDateTime) fila[0], (ZonedDateTime) fila[1]).toMinutes())
                .average()
                .stream().boxed().findFirst().orElse(null);
    }

    /**
     * Inicio del bloque al que pertenece un instante. Para las horas basta con
     * {@code truncatedTo}; para los dias no, porque truncar a dias opera sobre
     * el instante UTC y devolveria la medianoche equivocada en cualquier zona
     * que no sea la de Greenwich.
     */
    private ZonedDateTime truncar(ZonedDateTime instante, ChronoUnit unidad) {
        return unidad == ChronoUnit.DAYS
                ? instante.toLocalDate().atStartOfDay(instante.getZone())
                : instante.truncatedTo(ChronoUnit.HOURS);
    }

    private String nombreDe(Trabajador t) {
        String nombre = (t.getNombres() + " " + t.getApellidos()).trim();
        return nombre.isBlank() ? t.getUsername() : nombre;
    }
}
