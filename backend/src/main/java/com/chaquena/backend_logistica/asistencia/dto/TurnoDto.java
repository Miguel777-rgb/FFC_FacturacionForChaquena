package com.chaquena.backend_logistica.asistencia.dto;

import com.chaquena.backend_logistica.asistencia.domain.Turno;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Alta, edicion y lectura de un turno. Las horas son las del local. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnoDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @NotNull(message = "El turno necesita a quien le toca")
    private UUID trabajadorId;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String trabajadorNombre;

    @NotNull(message = "El turno necesita un dia")
    private LocalDate fecha;

    @NotNull(message = "El turno necesita hora de inicio")
    private LocalTime inicio;

    @NotNull(message = "El turno necesita hora de fin")
    private LocalTime fin;

    @Size(max = 200, message = "La nota admite hasta 200 caracteres")
    private String nota;

    /** Si la hora de fin cae al dia siguiente: el turno de la noche. */
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Boolean terminaAlDiaSiguiente;

    public static TurnoDto fromEntity(Turno t) {
        return TurnoDto.builder()
                .id(t.getId())
                .trabajadorId(t.getTrabajador().getId())
                .trabajadorNombre(nombreDe(t))
                .fecha(t.getFecha())
                .inicio(t.getInicio())
                .fin(t.getFin())
                .nota(t.getNota())
                .terminaAlDiaSiguiente(!t.getFin().isAfter(t.getInicio()))
                .build();
    }

    private static String nombreDe(Turno t) {
        return (t.getTrabajador().getNombres() + " " + t.getTrabajador().getApellidos()).trim();
    }
}
