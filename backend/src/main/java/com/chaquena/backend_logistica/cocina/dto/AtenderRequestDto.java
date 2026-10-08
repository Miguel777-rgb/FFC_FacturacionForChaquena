package com.chaquena.backend_logistica.cocina.dto;

import java.util.UUID;

/** El mozo responde «Voy» a este llamado. */
public record AtenderRequestDto(UUID llamadoId) {
}
