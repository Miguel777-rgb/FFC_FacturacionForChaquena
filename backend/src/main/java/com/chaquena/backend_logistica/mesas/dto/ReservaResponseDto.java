package com.chaquena.backend_logistica.mesas.dto;

import com.chaquena.backend_logistica.mesas.domain.EstadoReservaEnum;
import com.chaquena.backend_logistica.mesas.domain.Reserva;
import com.chaquena.backend_logistica.mesas.service.ReglaReservas;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaResponseDto {

    private UUID id;
    private UUID mesaId;
    private String mesaNumero;
    private String zona;
    private String nombre;
    private String celular;
    private Integer personas;
    private ZonedDateTime inicio;
    /** Inicio mas duracion, ya calculado para que la agenda no lo repita. */
    private ZonedDateTime fin;
    private Integer duracionMinutos;
    private EstadoReservaEnum estado;
    private String nota;
    /** A que puede pasar desde aqui, igual que en las comandas: la pantalla no copia la tabla. */
    private List<EstadoReservaEnum> transicionesPermitidas;

    public static ReservaResponseDto fromEntity(Reserva r) {
        return ReservaResponseDto.builder()
                .id(r.getId())
                .mesaId(r.getMesa().getId())
                .mesaNumero(r.getMesa().getNumero())
                .zona(r.getMesa().getZona())
                .nombre(r.getNombre())
                .celular(r.getCelular())
                .personas(r.getPersonas())
                .inicio(r.getInicio())
                .fin(ReglaReservas.fin(r))
                .duracionMinutos(r.getDuracionMinutos())
                .estado(r.getEstado())
                .nota(r.getNota())
                .transicionesPermitidas(ReglaReservas.transiciones(r.getEstado()))
                .build();
    }
}
