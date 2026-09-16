package com.chaquena.backend_logistica.asistencia.service;

import com.chaquena.backend_logistica.asistencia.domain.EstadoAsistenciaEnum;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Que es llegar tarde, que es faltar y cuantas horas se trabajaron. Sin Spring
 * ni base de datos.
 */
public final class ReglaAsistencia {

    /** Las horas del turno son las del local, no las del servidor. */
    public static final ZoneId ZONA_DEL_LOCAL = ZoneId.of("America/Lima");

    /** Diez minutos despues de la hora no es tardanza: es el micro que se demoro. */
    public static final int MINUTOS_DE_TOLERANCIA = 10;

    /** Una entrada cuenta para un turno si se marca desde dos horas antes de que empiece. */
    public static final int MINUTOS_ANTES_QUE_CUENTA = 120;

    /**
     * Una entrada abierta hace mas de esto es una salida que nadie marco. Sus
     * horas no se suman: contarlas inventaria un turno de dos dias.
     */
    public static final int HORAS_MAXIMAS_DENTRO = 16;

    /** Un tramo de tiempo. En una marcacion, {@code fin} nulo es que la persona sigue dentro. */
    public record Tramo(ZonedDateTime inicio, ZonedDateTime fin) {
    }

    public record Resumen(int turnos, int asistidos, int tardanzas, int inasistencias, long minutosTarde,
            long minutosTrabajados, int salidasSinMarcar) {
    }

    private ReglaAsistencia() {
    }

    /** Si termina a la misma hora o antes de empezar, termina al dia siguiente. */
    public static Tramo turno(LocalDate fecha, LocalTime inicio, LocalTime fin) {
        LocalDate diaDelFin = fin.isAfter(inicio) ? fecha : fecha.plusDays(1);
        return new Tramo(fecha.atTime(inicio).atZone(ZONA_DEL_LOCAL), diaDelFin.atTime(fin).atZone(ZONA_DEL_LOCAL));
    }

    public static boolean seSolapan(Tramo a, Tramo b) {
        return a.inicio().isBefore(b.fin()) && b.inicio().isBefore(a.fin());
    }

    /** La marcacion de un turno: la primera entrada desde dos horas antes hasta que el turno termina. */
    public static Optional<Tramo> marcacionDelTurno(Tramo turno, List<Tramo> marcaciones) {
        ZonedDateTime desde = turno.inicio().minusMinutes(MINUTOS_ANTES_QUE_CUENTA);
        return marcaciones.stream()
                .filter(m -> !m.inicio().isBefore(desde) && m.inicio().isBefore(turno.fin()))
                .min(Comparator.comparing(Tramo::inicio));
    }

    /** Cero dentro de la tolerancia; pasada, cuenta todo el retraso y no solo lo que la excede. */
    public static long minutosTarde(Tramo turno, Tramo marcacion) {
        long minutos = Duration.between(turno.inicio(), marcacion.inicio()).toMinutes();
        return minutos > MINUTOS_DE_TOLERANCIA ? minutos : 0;
    }

    /**
     * Un turno que todavia no empieza no cuenta. Uno que empezo y sigue sin
     * entrada tampoco: la persona todavia puede llegar, y llamarla ausente a
     * mitad de turno seria adelantarse. Solo es inasistencia cuando el turno ya
     * termino sin ninguna entrada.
     */
    public static Resumen resumir(List<Tramo> turnos, List<Tramo> marcaciones, ZonedDateTime ahora) {
        int iniciados = 0;
        int asistidos = 0;
        int tardanzas = 0;
        int inasistencias = 0;
        long minutosTarde = 0;
        for (Tramo turno : turnos) {
            if (turno.inicio().isAfter(ahora)) {
                continue;
            }
            iniciados++;
            Optional<Tramo> marcacion = marcacionDelTurno(turno, marcaciones);
            if (marcacion.isPresent()) {
                asistidos++;
                long tarde = minutosTarde(turno, marcacion.get());
                if (tarde > 0) {
                    tardanzas++;
                    minutosTarde += tarde;
                }
            } else if (!turno.fin().isAfter(ahora)) {
                inasistencias++;
            }
        }

        long minutosTrabajados = 0;
        int salidasSinMarcar = 0;
        for (Tramo m : marcaciones) {
            if (m.fin() != null) {
                minutosTrabajados += Duration.between(m.inicio(), m.fin()).toMinutes();
            } else if (Duration.between(m.inicio(), ahora).toHours() < HORAS_MAXIMAS_DENTRO) {
                minutosTrabajados += Duration.between(m.inicio(), ahora).toMinutes();
            } else {
                salidasSinMarcar++;
            }
        }
        return new Resumen(iniciados, asistidos, tardanzas, inasistencias, minutosTarde, minutosTrabajados,
                salidasSinMarcar);
    }

    /** Como va alguien respecto de un turno. Sin turno, lo dice la marcacion sola. */
    public static EstadoAsistenciaEnum estado(Tramo turno, Tramo marcacion, ZonedDateTime ahora) {
        if (marcacion != null) {
            return marcacion.fin() == null ? EstadoAsistenciaEnum.DENTRO : EstadoAsistenciaEnum.SALIO;
        }
        if (turno == null || ahora.isBefore(turno.inicio().plusMinutes(MINUTOS_DE_TOLERANCIA))) {
            return EstadoAsistenciaEnum.POR_LLEGAR;
        }
        return ahora.isBefore(turno.fin()) ? EstadoAsistenciaEnum.NO_LLEGA : EstadoAsistenciaEnum.FALTO;
    }
}
