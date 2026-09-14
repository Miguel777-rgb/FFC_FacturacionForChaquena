package com.chaquena.backend_logistica.local.dto;

import com.chaquena.backend_logistica.local.domain.HorarioLocal;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HorarioLocalDto {

    @NotNull(message = "Cada horario necesita su dia")
    private DayOfWeek dia;

    /** Nula si el dia esta cerrado o si todavia no se definio. */
    private LocalTime abre;

    private LocalTime cierra;

    private Boolean cerrado;

    public static HorarioLocalDto fromEntity(HorarioLocal h) {
        return HorarioLocalDto.builder()
                .dia(h.getDia())
                .abre(h.getAbre())
                .cierra(h.getCierra())
                .cerrado(h.getCerrado())
                .build();
    }
}
