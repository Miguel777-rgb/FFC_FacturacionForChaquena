package com.chaquena.backend_logistica.clientes.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpresaRequestDto {

    @NotBlank(message = "El RUC es obligatorio")
    @Pattern(regexp = "\\d{11}", message = "El RUC debe tener exactamente 11 digitos")
    private String ruc;

    @NotBlank(message = "La razon social es obligatoria")
    @Size(max = 150, message = "La razon social no puede exceder 150 caracteres")
    private String razonSocial;

    @Size(max = 20, message = "El celular no puede exceder 20 caracteres")
    private String celular;

    private String direccionFiscal;
}
