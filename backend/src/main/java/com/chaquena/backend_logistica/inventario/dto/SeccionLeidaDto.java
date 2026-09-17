package com.chaquena.backend_logistica.inventario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Los productos que quedaron bajo un mismo titulo de la carta. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeccionLeidaDto {

    /** El titulo tal como se leyo, ya en mayuscula inicial. Nulo si el producto no tenia titulo encima. */
    private String nombre;

    private List<PlatilloLeidoDto> platillos;
}
