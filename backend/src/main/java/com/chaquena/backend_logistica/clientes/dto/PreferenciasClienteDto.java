package com.chaquena.backend_logistica.clientes.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * Notas de excepcion que el cliente pidio en comandas anteriores ("sin
 * cebolla", "bien cocido"), para que el mozo pueda sugerirlas de nuevo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreferenciasClienteDto {

    private UUID clienteId;
    private List<String> notasHabituales;
    private List<String> platillosFrecuentes;
}
