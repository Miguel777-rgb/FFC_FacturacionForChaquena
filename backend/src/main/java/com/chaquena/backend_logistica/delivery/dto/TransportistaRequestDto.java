package com.chaquena.backend_logistica.delivery.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransportistaRequestDto {

    @NotBlank(message = "El DNI del conductor es obligatorio para la auditoria de despacho")
    @Size(max = 15, message = "El DNI no puede exceder 15 caracteres")
    private String dni;

    @NotBlank(message = "Los nombres del conductor son obligatorios")
    @Size(max = 100, message = "Los nombres no pueden exceder 100 caracteres")
    private String nombres;

    @NotBlank(message = "Los apellidos del conductor son obligatorios")
    @Size(max = 100, message = "Los apellidos no pueden exceder 100 caracteres")
    private String apellidos;

    @NotBlank(message = "El telefono es obligatorio para coordinar la entrega")
    @Size(max = 20, message = "El telefono no puede exceder 20 caracteres")
    private String celular;

    @Email(message = "El correo no tiene un formato valido")
    private String correo;

    @NotBlank(message = "La empresa de transporte es obligatoria")
    @Size(max = 100, message = "La empresa no puede exceder 100 caracteres")
    private String empresaTransporte;
}
