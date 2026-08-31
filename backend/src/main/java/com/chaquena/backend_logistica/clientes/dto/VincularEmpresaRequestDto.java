package com.chaquena.backend_logistica.clientes.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VincularEmpresaRequestDto {

    @NotNull(message = "La empresa es obligatoria")
    private UUID empresaId;

    @Size(max = 100, message = "El cargo no puede exceder 100 caracteres")
    private String cargoEnEmpresa;
}
