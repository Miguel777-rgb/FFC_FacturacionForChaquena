package com.chaquena.backend_logistica.pedidos.dto;

import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CambioEstadoRequestDto {

    @NotNull(message = "El estado destino es obligatorio")
    private EstadoOrdenEnum estado;

    private String observacion;
}
