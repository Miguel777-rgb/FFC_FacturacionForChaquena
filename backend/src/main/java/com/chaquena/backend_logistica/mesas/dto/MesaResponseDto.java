package com.chaquena.backend_logistica.mesas.dto;

import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.domain.FormaMesaEnum;
import com.chaquena.backend_logistica.mesas.domain.Mesa;
import com.chaquena.backend_logistica.mesas.domain.Reserva;
import com.chaquena.backend_logistica.mesas.service.ReglaReservas;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MesaResponseDto {

    private UUID id;
    private String numero;
    private String zona;
    private Integer capacidad;
    /**
     * Libre, ocupada o inhabilitada, como la guarda la mesa; o reservada si una
     * reserva activa la esta apartando ahora mismo.
     */
    private EstadoMesaEnum estado;
    private Boolean activa;
    private Integer columna;
    private Integer fila;
    private Integer ancho;
    private Integer alto;
    private FormaMesaEnum forma;
    /** La siguiente reserva activa de hoy que todavia no termino, aparte o no la mesa. */
    private ReservaResponseDto reservaProxima;

    public static MesaResponseDto fromEntity(Mesa m) {
        return deSalon(m, null, null);
    }

    public static MesaResponseDto deSalon(Mesa m, Reserva proxima, ZonedDateTime ahora) {
        boolean apartada = proxima != null && ahora != null && m.getEstado() == EstadoMesaEnum.LIBRE
                && ReglaReservas.apartaLaMesa(proxima, ahora);
        return MesaResponseDto.builder()
                .id(m.getId())
                .numero(m.getNumero())
                .zona(m.getZona())
                .capacidad(m.getCapacidad())
                .estado(apartada ? EstadoMesaEnum.RESERVADA : m.getEstado())
                .activa(m.getActiva())
                .columna(m.getColumna())
                .fila(m.getFila())
                .ancho(m.getAncho())
                .alto(m.getAlto())
                .forma(m.getForma())
                .reservaProxima(proxima != null ? ReservaResponseDto.fromEntity(proxima) : null)
                .build();
    }
}
