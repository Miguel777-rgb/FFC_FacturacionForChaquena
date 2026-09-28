package com.chaquena.backend_logistica.clientes.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteRequestDto {

    @NotBlank(message = "El documento de identidad es obligatorio")
    @Size(max = 15, message = "El documento no puede exceder 15 caracteres")
    private String dni;

    @NotBlank(message = "Los nombres son obligatorios")
    @Size(max = 100, message = "Los nombres no pueden exceder 100 caracteres")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 100, message = "Los apellidos no pueden exceder 100 caracteres")
    private String apellidos;

    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 100, message = "El correo no puede exceder 100 caracteres")
    private String correo;

    @Size(max = 20, message = "El celular no puede exceder 20 caracteres")
    private String celular;

    private String direccionHabitual;

    /** El punto de la direccion habitual. Van las dos o ninguna. */
    @DecimalMin(value = "-90.0", message = "La latitud va de -90 a 90")
    @DecimalMax(value = "90.0", message = "La latitud va de -90 a 90")
    private Double latitud;

    @DecimalMin(value = "-180.0", message = "La longitud va de -180 a 180")
    @DecimalMax(value = "180.0", message = "La longitud va de -180 a 180")
    private Double longitud;

    @Size(max = 50, message = "El tipo de cliente no puede exceder 50 caracteres")
    private String tipoCliente;
}
