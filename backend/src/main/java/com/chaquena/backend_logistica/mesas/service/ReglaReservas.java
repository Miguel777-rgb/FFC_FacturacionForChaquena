package com.chaquena.backend_logistica.mesas.service;

import com.chaquena.backend_logistica.mesas.domain.EstadoReservaEnum;
import com.chaquena.backend_logistica.mesas.domain.Reserva;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.chaquena.backend_logistica.mesas.domain.EstadoReservaEnum.*;

/**
 * Cuando una reserva aparta una mesa, con cual choca y a que puede pasar. Sin
 * Spring ni base de datos.
 */
public final class ReglaReservas {

    /** El dia de la agenda es el de Lima, no el del servidor. */
    public static final ZoneId ZONA_DEL_LOCAL = ZoneId.of("America/Lima");

    /** Lo que dura una cena si nadie dice otra cosa. */
    public static final int DURACION_POR_DEFECTO = 90;

    public static final int DURACION_MAXIMA = 480;

    /**
     * Desde cuanto antes la mesa se ve reservada. Una hora deja al mozo no
     * sentar a nadie que no vaya a terminar a tiempo; mas, y la mesa pasaria
     * la tarde vacia esperando la cena.
     */
    public static final int MINUTOS_ANTES_QUE_APARTA = 60;

    /** Las que todavia pueden llegar: son las unicas que apartan la mesa. */
    public static final Set<EstadoReservaEnum> ACTIVAS = Set.copyOf(EnumSet.of(PENDIENTE, CONFIRMADA));

    private static final Map<EstadoReservaEnum, List<EstadoReservaEnum>> TRANSICIONES = Map.of(
            PENDIENTE, List.of(CONFIRMADA, CUMPLIDA, NO_ASISTIO, CANCELADA),
            CONFIRMADA, List.of(CUMPLIDA, NO_ASISTIO, CANCELADA));

    private ReglaReservas() {
    }

    /** Cumplida, cancelada y no asistio son finales: la historia no se reescribe. */
    public static List<EstadoReservaEnum> transiciones(EstadoReservaEnum estado) {
        return TRANSICIONES.getOrDefault(estado, List.of());
    }

    public static ZonedDateTime fin(ZonedDateTime inicio, int duracionMinutos) {
        return inicio.plusMinutes(duracionMinutos);
    }

    public static ZonedDateTime fin(Reserva reserva) {
        return fin(reserva.getInicio(), reserva.getDuracionMinutos());
    }

    /** Dos reservas se pisan si una empieza antes de que termine la otra. Terminar a las 21:30 y empezar a las 21:30 no choca. */
    public static boolean seSolapan(ZonedDateTime inicioA, int duracionA, ZonedDateTime inicioB, int duracionB) {
        return inicioA.isBefore(fin(inicioB, duracionB)) && inicioB.isBefore(fin(inicioA, duracionA));
    }

    public static boolean apartaLaMesa(EstadoReservaEnum estado, ZonedDateTime inicio, int duracionMinutos,
            ZonedDateTime ahora) {
        return ACTIVAS.contains(estado)
                && !ahora.isBefore(inicio.minusMinutes(MINUTOS_ANTES_QUE_APARTA))
                && ahora.isBefore(fin(inicio, duracionMinutos));
    }

    public static boolean apartaLaMesa(Reserva reserva, ZonedDateTime ahora) {
        return apartaLaMesa(reserva.getEstado(), reserva.getInicio(), reserva.getDuracionMinutos(), ahora);
    }
}
