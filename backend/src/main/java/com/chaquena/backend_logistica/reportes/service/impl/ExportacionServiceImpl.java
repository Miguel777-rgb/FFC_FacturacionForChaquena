package com.chaquena.backend_logistica.reportes.service.impl;

import com.chaquena.backend_logistica.asistencia.domain.EstadoAsistenciaEnum;
import com.chaquena.backend_logistica.asistencia.dto.AsistenciaDelDiaDto;
import com.chaquena.backend_logistica.asistencia.service.AsistenciaService;
import com.chaquena.backend_logistica.inventario.dto.InsumoResponseDto;
import com.chaquena.backend_logistica.inventario.service.InsumoService;
import com.chaquena.backend_logistica.reportes.dto.FormatoExportacionEnum;
import com.chaquena.backend_logistica.reportes.dto.GranularidadEnum;
import com.chaquena.backend_logistica.reportes.dto.ProductoTopDto;
import com.chaquena.backend_logistica.reportes.dto.ReporteVentasDto;
import com.chaquena.backend_logistica.reportes.dto.SerieVentasDto;
import com.chaquena.backend_logistica.reportes.dto.VentasPorMetodoPagoDto;
import com.chaquena.backend_logistica.reportes.dto.VentasPorMozoDto;
import com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable;
import com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable.Columna;
import com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable.Tabla;
import com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable.TipoCelda;
import com.chaquena.backend_logistica.reportes.exportacion.EscritorPdf;
import com.chaquena.backend_logistica.reportes.exportacion.EscritorXlsx;
import com.chaquena.backend_logistica.reportes.exportacion.Rotulos;
import com.chaquena.backend_logistica.reportes.service.ExportacionService;
import com.chaquena.backend_logistica.reportes.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import static com.chaquena.backend_logistica.reportes.exportacion.DocumentoExportable.TipoCelda.*;

/**
 * Arma cada reporte con los mismos servicios que alimentan las pantallas y lo
 * entrega escrito en el formato pedido. No hay consultas propias: lo que dice
 * el archivo es lo que dice la pantalla con el mismo rango.
 */
@Service
@RequiredArgsConstructor
public class ExportacionServiceImpl implements ExportacionService {

    /** Un ano de asistencia son 732 consultas; mas ya no es un reporte sino un volcado. */
    static final long DIAS_MAXIMOS_DE_ASISTENCIA = 366;

    private static final int INSUMOS_MAXIMOS = 5_000;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ReporteService reporteService;
    private final InsumoService insumoService;
    private final AsistenciaService asistenciaService;

    @Override
    @Transactional(readOnly = true)
    public ArchivoExportado ventas(ZonedDateTime desde, ZonedDateTime hasta, FormatoExportacionEnum formato,
            Locale locale) {
        Rotulos r = Rotulos.para(locale);
        ReporteVentasDto ventas = reporteService.ventas(desde, hasta);
        List<VentasPorMetodoPagoDto> porMetodo = reporteService.ventasPorMetodoPago(desde, hasta);
        SerieVentasDto porDia = reporteService.serieVentas(desde, hasta, GranularidadEnum.DIA);
        List<VentasPorMozoDto> porMozo = reporteService.ventasPorMozo(desde, hasta);

        long comandas = ventas.getPorCanal().stream().mapToLong(ReporteVentasDto.PorCanal::getCantidadComandas).sum();
        BigDecimal ticket = comandas == 0 ? BigDecimal.ZERO
                : ventas.getTotalVendido().divide(BigDecimal.valueOf(comandas), 2, RoundingMode.HALF_UP);

        List<Tabla> tablas = List.of(
                new Tabla(r.texto("ventas.resumen"),
                        columnas(r, "ventas.totalVendido", SOLES, "ventas.comandas", ENTERO, "ventas.ticketPromedio", SOLES),
                        List.of(Arrays.asList(ventas.getTotalVendido(), comandas, ticket)),
                        null),
                new Tabla(r.texto("ventas.porCanal"),
                        columnas(r, "ventas.canal", TEXTO, "ventas.comandas", ENTERO, "total", SOLES),
                        filas(ventas.getPorCanal(), c -> Arrays.asList(r.valor(c.getCanal()), c.getCantidadComandas(), c.getTotal())),
                        pieDeTotales(r, ventas.getPorCanal().stream().mapToLong(ReporteVentasDto.PorCanal::getCantidadComandas).sum(),
                                ventas.getTotalVendido())),
                new Tabla(r.texto("ventas.porMetodo"),
                        columnas(r, "ventas.metodo", TEXTO, "ventas.pagos", ENTERO, "total", SOLES),
                        filas(porMetodo, m -> Arrays.asList(r.valor(m.getMetodo()), m.getPagos(), m.getTotal())),
                        pieDeTotales(r, porMetodo.stream().mapToLong(VentasPorMetodoPagoDto::getPagos).sum(),
                                suma(porMetodo, VentasPorMetodoPagoDto::getTotal))),
                new Tabla(r.texto("ventas.porDia"),
                        columnas(r, "ventas.fecha", FECHA, "ventas.comandas", ENTERO, "total", SOLES),
                        filas(porDia.getPuntos(), p -> Arrays.asList(p.getInicio().toLocalDate(), p.getComandas(), p.getTotal())),
                        pieDeTotales(r, porDia.getPuntos().stream().mapToLong(SerieVentasDto.Punto::getComandas).sum(),
                                suma(porDia.getPuntos(), SerieVentasDto.Punto::getTotal))),
                new Tabla(r.texto("ventas.porMozo"),
                        columnas(r, "ventas.mozo", TEXTO, "ventas.comandas", ENTERO, "total", SOLES,
                                "ventas.ticketPromedio", SOLES),
                        filas(porMozo, m -> Arrays.asList(m.getMozo() != null ? m.getMozo() : "—", m.getComandas(),
                                m.getTotal(), m.getTicketPromedio())),
                        null));

        return escribir(new DocumentoExportable(r.texto("ventas.titulo"), rango(r, desde, hasta), tablas),
                "ventas-" + sufijoDeRango(desde, hasta), formato, r);
    }

    @Override
    @Transactional(readOnly = true)
    public ArchivoExportado productos(ZonedDateTime desde, ZonedDateTime hasta, FormatoExportacionEnum formato,
            Locale locale) {
        Rotulos r = Rotulos.para(locale);
        List<ProductoTopDto> productos = reporteService.productosTop(desde, hasta, Integer.MAX_VALUE);

        Tabla tabla = new Tabla(r.texto("productos.titulo"),
                columnas(r, "productos.platillo", TEXTO, "productos.unidades", ENTERO, "productos.monto", SOLES),
                filas(productos, p -> Arrays.asList(p.getPlatillo(), p.getUnidadesVendidas(), p.getMontoTotal())),
                pieDeTotales(r, productos.stream().mapToLong(ProductoTopDto::getUnidadesVendidas).sum(),
                        suma(productos, ProductoTopDto::getMontoTotal)));

        return escribir(new DocumentoExportable(r.texto("productos.titulo"), rango(r, desde, hasta), List.of(tabla)),
                "platillos-" + sufijoDeRango(desde, hasta), formato, r);
    }

    @Override
    @Transactional(readOnly = true)
    public ArchivoExportado inventario(FormatoExportacionEnum formato, Locale locale) {
        Rotulos r = Rotulos.para(locale);
        List<InsumoResponseDto> insumos = insumoService
                .buscar(null, null, false, PageRequest.of(0, INSUMOS_MAXIMOS, Sort.by("nombre")))
                .getContenido();

        List<Object> pie = new ArrayList<>(List.of(r.texto("total"), "", "", "", "", "", "", "", ""));
        pie.add(suma(insumos, InsumoResponseDto::getValorStock));

        Tabla tabla = new Tabla(r.texto("inventario.titulo"),
                columnas(r, "inventario.insumo", TEXTO, "inventario.tipo", TEXTO, "inventario.unidad", TEXTO,
                        "inventario.stock", CANTIDAD, "inventario.minimo", CANTIDAD,
                        "inventario.vencimiento", FECHA, "inventario.porVencer", CANTIDAD,
                        "inventario.vencido", CANTIDAD, "inventario.sinCosto", CANTIDAD, "inventario.valor", SOLES),
                filas(insumos, i -> Arrays.asList(i.getNombre(), r.valor(i.getTipoInsumo()), i.getUnidadMedida(),
                        i.getStockActual(), i.getStockMinimo(), i.getProximoVencimiento(), i.getCantidadPorVencer(),
                        i.getCantidadVencida(), i.getCantidadSinCosto(), i.getValorStock())),
                pie);

        ZonedDateTime ahora = ZonedDateTime.now(Rotulos.ZONA_DEL_LOCAL);
        return escribir(new DocumentoExportable(r.texto("inventario.titulo"),
                        r.texto("alMomento", r.fechaHora(ahora)), List.of(tabla)),
                "inventario-" + ahora.toLocalDate(), formato, r);
    }

    @Override
    @Transactional(readOnly = true)
    public ArchivoExportado asistencia(LocalDate desde, LocalDate hasta, FormatoExportacionEnum formato,
            Locale locale) {
        // Al reves no es un error que valga la pena devolver: se entiende igual.
        LocalDate inicio = desde.isAfter(hasta) ? hasta : desde;
        LocalDate fin = desde.isAfter(hasta) ? desde : hasta;
        if (ChronoUnit.DAYS.between(inicio, fin) >= DIAS_MAXIMOS_DE_ASISTENCIA) {
            throw new IllegalArgumentException("La asistencia se exporta por un ano como mucho.");
        }

        Rotulos r = Rotulos.para(locale);
        List<List<Object>> detalle = new ArrayList<>();
        Map<UUID, Acumulado> porPersona = new LinkedHashMap<>();

        for (LocalDate dia = inicio; !dia.isAfter(fin); dia = dia.plusDays(1)) {
            for (AsistenciaDelDiaDto fila : asistenciaService.delDia(dia)) {
                detalle.add(Arrays.asList(dia, fila.getNombre(), fila.getCargo(), turno(fila, r), fila.getEntrada(),
                        fila.getSalida(), r.valor(fila.getEstado()), fila.getMinutosTarde()));
                porPersona.computeIfAbsent(fila.getTrabajadorId(), id -> new Acumulado(fila.getNombre(), fila.getCargo()))
                        .sumar(fila);
            }
        }

        Tabla resumen = new Tabla(r.texto("asistencia.resumen"),
                columnas(r, "asistencia.persona", TEXTO, "asistencia.cargo", TEXTO, "asistencia.turnos", ENTERO,
                        "asistencia.faltas", ENTERO, "asistencia.tardanzas", ENTERO,
                        "asistencia.minutosTarde", ENTERO, "asistencia.horas", CANTIDAD),
                filas(List.copyOf(porPersona.values()), a -> Arrays.asList(a.nombre, a.cargo != null ? a.cargo : "",
                        a.turnos, a.faltas, a.tardanzas, a.minutosTarde,
                        BigDecimal.valueOf(a.minutosDentro).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP))),
                null);
        Tabla porDia = new Tabla(r.texto("asistencia.detalle"),
                columnas(r, "asistencia.fecha", FECHA, "asistencia.persona", TEXTO, "asistencia.cargo", TEXTO,
                        "asistencia.turno", TEXTO, "asistencia.entrada", FECHA_HORA, "asistencia.salida", FECHA_HORA,
                        "asistencia.estado", TEXTO, "asistencia.minutosTarde", ENTERO),
                detalle,
                null);

        return escribir(new DocumentoExportable(r.texto("asistencia.titulo"),
                        r.texto("rango", r.fecha(inicio), r.fecha(fin)), List.of(resumen, porDia)),
                "asistencia-" + inicio + "_" + fin, formato, r);
    }

    // --- apoyos ---------------------------------------------------------------

    private static final class Acumulado {
        final String nombre;
        final String cargo;
        long turnos;
        long faltas;
        long tardanzas;
        long minutosTarde;
        long minutosDentro;

        Acumulado(String nombre, String cargo) {
            this.nombre = nombre;
            this.cargo = cargo;
        }

        void sumar(AsistenciaDelDiaDto fila) {
            if (fila.getTurnoInicio() != null) {
                turnos++;
            }
            if (fila.getEstado() == EstadoAsistenciaEnum.FALTO) {
                faltas++;
            }
            if (fila.getMinutosTarde() > 0) {
                tardanzas++;
                minutosTarde += fila.getMinutosTarde();
            }
            if (fila.getEntrada() != null && fila.getSalida() != null) {
                minutosDentro += Duration.between(fila.getEntrada(), fila.getSalida()).toMinutes();
            }
        }
    }

    private ArchivoExportado escribir(DocumentoExportable documento, String nombre, FormatoExportacionEnum formato,
            Rotulos rotulos) {
        byte[] contenido = formato == FormatoExportacionEnum.XLSX
                ? EscritorXlsx.escribir(documento, rotulos)
                : EscritorPdf.escribir(documento, rotulos);
        return new ArchivoExportado(contenido, "chaquena-" + nombre + "." + formato.extension(), formato);
    }

    /** Pares nombre-tipo: {@code columnas(r, "ventas.canal", TEXTO, "total", SOLES)}. */
    private static List<Columna> columnas(Rotulos r, Object... pares) {
        List<Columna> columnas = new ArrayList<>();
        for (int i = 0; i < pares.length; i += 2) {
            columnas.add(new Columna(r.texto((String) pares[i]), (TipoCelda) pares[i + 1]));
        }
        return columnas;
    }

    private static <T> List<List<Object>> filas(List<T> elementos, Function<T, List<Object>> fila) {
        return elementos.stream().map(fila).toList();
    }

    private static List<Object> pieDeTotales(Rotulos r, long cantidad, BigDecimal total) {
        return Arrays.asList(r.texto("total"), cantidad, total);
    }

    private static <T> BigDecimal suma(List<T> elementos, Function<T, BigDecimal> monto) {
        return elementos.stream().map(monto).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String rango(Rotulos r, ZonedDateTime desde, ZonedDateTime hasta) {
        return r.texto("rango", r.fecha(enElLocal(desde)), r.fecha(enElLocal(hasta)));
    }

    private static String sufijoDeRango(ZonedDateTime desde, ZonedDateTime hasta) {
        return enElLocal(desde) + "_" + enElLocal(hasta);
    }

    private static LocalDate enElLocal(ZonedDateTime instante) {
        return instante.withZoneSameInstant(Rotulos.ZONA_DEL_LOCAL).toLocalDate();
    }

    private static String turno(AsistenciaDelDiaDto fila, Rotulos r) {
        if (fila.getTurnoInicio() == null) {
            return r.texto("asistencia.sinTurno");
        }
        return HORA.format(fila.getTurnoInicio().withZoneSameInstant(Rotulos.ZONA_DEL_LOCAL)) + " – "
                + HORA.format(fila.getTurnoFin().withZoneSameInstant(Rotulos.ZONA_DEL_LOCAL));
    }
}
