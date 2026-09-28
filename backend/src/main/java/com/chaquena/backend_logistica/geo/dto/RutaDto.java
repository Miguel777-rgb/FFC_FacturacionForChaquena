package com.chaquena.backend_logistica.geo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Distancia y tiempo por calles desde el local hasta un punto, en auto. Es
 * informativo: no se guarda ni promete nada, y el reparto lo hace una empresa
 * externa con sus propios vehiculos. Nulos si no se pudo calcular.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RutaDto {

    private Integer metros;
    private Integer segundos;
}
