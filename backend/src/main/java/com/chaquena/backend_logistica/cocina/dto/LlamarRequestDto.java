package com.chaquena.backend_logistica.cocina.dto;

import java.util.UUID;

/** Cocina llama al mozo para esta comanda. */
public record LlamarRequestDto(UUID ordenId) {
}
