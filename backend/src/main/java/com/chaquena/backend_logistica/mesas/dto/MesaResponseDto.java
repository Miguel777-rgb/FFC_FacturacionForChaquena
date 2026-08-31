package com.chaquena.backend_logistica.mesas.dto;

import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.domain.Mesa;
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
    private EstadoMesaEnum estado;
    private String reservadaANombreDe;
    private ZonedDateTime reservadaPara;
    private Boolean activa;

    public static MesaResponseDto fromEntity(Mesa m) {
        return MesaResponseDto.builder()
                .id(m.getId())
                .numero(m.getNumero())
                .zona(m.getZona())
                .capacidad(m.getCapacidad())
                .estado(m.getEstado())
                .reservadaANombreDe(m.getReservadaANombreDe())
                .reservadaPara(m.getReservadaPara())
                .activa(m.getActiva())
                .build();
    }
}
