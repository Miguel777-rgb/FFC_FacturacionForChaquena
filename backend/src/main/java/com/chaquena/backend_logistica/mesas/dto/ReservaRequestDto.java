package com.chaquena.backend_logistica.mesas.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaRequestDto {

    @NotNull(message = "La reserva necesita una mesa")
    private UUID mesaId;

    @NotBlank(message = "La reserva necesita un nombre")
    @Size(max = 120, message = "El nombre admite hasta 120 caracteres")
    private String nombre;

    @Size(max = 20, message = "El celular admite hasta 20 caracteres")
    @Pattern(regexp = "[0-9+ ]*", message = "El celular solo lleva digitos, espacios y +")
    private String celular;

    @NotNull(message = "La reserva necesita cuantas personas vienen")
    @Min(value = 1, message = "La reserva es para al menos una persona")
    @Max(value = 50, message = "Una reserva de mas de 50 personas es un evento, no una mesa")
    private Integer personas;

    @NotNull(message = "La reserva necesita una hora")
    private ZonedDateTime inicio;

    /** Sin duracion se aparta la mesa 90 minutos. */
    @Min(value = 15, message = "Una reserva dura al menos 15 minutos")
    @Max(value = 480, message = "Una reserva dura como mucho 8 horas")
    private Integer duracionMinutos;

    @Size(max = 500, message = "La nota admite hasta 500 caracteres")
    private String nota;
}
