package com.chaquena.backend_logistica.pedidos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelarOrdenRequestDto {

    @NotBlank(message = "El motivo de cancelacion es obligatorio para el historico")
    private String motivo;

    /** Por defecto se devuelven los insumos al stock. */
    private Boolean reponerStock;
}
