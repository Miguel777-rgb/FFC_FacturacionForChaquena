package com.chaquena.backend_logistica.asistencia.service.impl;

import com.chaquena.backend_logistica.asistencia.dto.DesempenoDto;
import com.chaquena.backend_logistica.asistencia.repository.MarcacionRepository;
import com.chaquena.backend_logistica.asistencia.repository.TurnoRepository;
import com.chaquena.backend_logistica.asistencia.service.DesempenoService;
import com.chaquena.backend_logistica.asistencia.service.ReglaAsistencia;
import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.pedidos.repository.CalificacionFeedbackRepository;
import com.chaquena.backend_logistica.reportes.dto.VentasPorMozoDto;
import com.chaquena.backend_logistica.reportes.service.ReporteService;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Junta tres fuentes que ya existian y una nueva: la venta sale del mismo
 * reporte de ventas por mozo, la atencion de las calificaciones de sus
 * comandas, y la puntualidad de comparar turnos con marcaciones.
 */
@Service
@RequiredArgsConstructor
public class DesempenoServiceImpl implements DesempenoService {

    private static final int DIAS_POR_DEFECTO = 30;
    private static final BigDecimal SESENTA = BigDecimal.valueOf(60);

    private final TrabajadorRepository trabajadorRepository;
    private final TurnoRepository turnoRepository;
    private final MarcacionRepository marcacionRepository;
    private final CalificacionFeedbackRepository calificacionRepository;
    private final ReporteService reporteService;

    @Override
    @Transactional(readOnly = true)
    public DesempenoDto de(UUID trabajadorId, LocalDate desde, LocalDate hasta) {
        Trabajador trabajador = trabajadorRepository.findById(trabajadorId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el trabajador", trabajadorId));

        ZonedDateTime ahora = ZonedDateTime.now(ReglaAsistencia.ZONA_DEL_LOCAL);
        LocalDate fin = hasta != null ? hasta : ahora.toLocalDate();
        LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO - 1L);
        if (inicio.isAfter(fin)) {
            throw new IllegalArgumentException("El rango empieza despues de terminar.");
        }
        ZonedDateTime zDesde = inicio.atStartOfDay(ReglaAsistencia.ZONA_DEL_LOCAL);
        ZonedDateTime zHasta = fin.plusDays(1).atStartOfDay(ReglaAsistencia.ZONA_DEL_LOCAL);
        // Un rango que ya termino se mide hasta su final, no hasta hoy.
        ZonedDateTime corte = ahora.isBefore(zHasta) ? ahora : zHasta;

        VentasPorMozoDto ventas = reporteService.ventasPorMozo(zDesde, zHasta).stream()
                .filter(v -> trabajadorId.equals(v.getMozoId()))
                .findFirst()
                .orElse(null);

        Double atencion = calificacionRepository.promedioAtencionDelMozo(trabajadorId, zDesde, zHasta);
        long calificaciones = calificacionRepository.calificacionesDelMozo(trabajadorId, zDesde, zHasta);

        List<ReglaAsistencia.Tramo> turnos = turnoRepository
                .findByTrabajadorIdAndFechaBetweenOrderByFechaAscInicioAsc(trabajadorId, inicio, fin).stream()
                .map(t -> ReglaAsistencia.turno(t.getFecha(), t.getInicio(), t.getFin()))
                .toList();
        List<ReglaAsistencia.Tramo> marcaciones = marcacionRepository
                .findByTrabajadorIdAndEntradaBetweenOrderByEntradaAsc(trabajadorId,
                        zDesde.minusMinutes(ReglaAsistencia.MINUTOS_ANTES_QUE_CUENTA), zHasta)
                .stream()
                .map(m -> new ReglaAsistencia.Tramo(m.getEntrada(), m.getSalida()))
                .toList();
        ReglaAsistencia.Resumen resumen = ReglaAsistencia.resumir(turnos, marcaciones, corte);

        return DesempenoDto.builder()
                .trabajadorId(trabajadorId)
                .nombre((trabajador.getNombres() + " " + trabajador.getApellidos()).trim())
                .cargo(trabajador.getCargo() != null ? trabajador.getCargo().getNombre() : null)
                .desde(inicio)
                .hasta(fin)
                .comandas(ventas != null ? ventas.getComandas() : 0)
                .vendido(ventas != null ? ventas.getTotal() : BigDecimal.ZERO)
                .ticketPromedio(ventas != null ? ventas.getTicketPromedio() : BigDecimal.ZERO)
                .satisfaccionAtencion(calificaciones > 0 && atencion != null
                        ? BigDecimal.valueOf(atencion).setScale(1, RoundingMode.HALF_UP)
                        : null)
                .calificaciones(calificaciones)
                .turnos(resumen.turnos())
                .asistidos(resumen.asistidos())
                .tardanzas(resumen.tardanzas())
                .inasistencias(resumen.inasistencias())
                .minutosTarde(resumen.minutosTarde())
                .horasTrabajadas(BigDecimal.valueOf(resumen.minutosTrabajados()).divide(SESENTA, 1, RoundingMode.HALF_UP))
                .salidasSinMarcar(resumen.salidasSinMarcar())
                .build();
    }
}
