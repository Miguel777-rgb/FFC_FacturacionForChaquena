package com.chaquena.backend_logistica.cocina.dto;

import com.chaquena.backend_logistica.cocina.domain.EstadoLlamadoEnum;
import com.chaquena.backend_logistica.cocina.domain.LlamadoCocina;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Un llamado tal como lo ven las pantallas.
 *
 * <p>No lleva una frase hecha («Mesa 3 lista»): lleva la mesa y el tipo de
 * comanda, y cada pantalla compone el texto en su idioma.
 *
 * @param correlativo        los ocho primeros caracteres del id, como en cocina
 * @param segundosRespuesta  lo que tardo el mozo en decir «Voy»; nulo si nadie respondio
 */
public record LlamadoCocinaDto(
        UUID id,
        UUID ordenId,
        String correlativo,
        TipoOrdenEnum tipoOrden,
        String mesaNumero,
        String llamadoPor,
        ZonedDateTime llamadoEn,
        EstadoLlamadoEnum estado,
        String atendidoPor,
        ZonedDateTime atendidoEn,
        Long segundosRespuesta) {

    public static LlamadoCocinaDto de(LlamadoCocina llamado) {
        Orden orden = llamado.getOrden();
        Long segundos = llamado.getAtendidoEn() == null ? null
                : Duration.between(llamado.getLlamadoEn(), llamado.getAtendidoEn()).toSeconds();
        return new LlamadoCocinaDto(
                llamado.getId(),
                orden.getId(),
                orden.getId().toString().substring(0, 8).toUpperCase(),
                orden.getTipoOrden(),
                orden.getMesaNumero(),
                llamado.getLlamadoPorNombre(),
                llamado.getLlamadoEn(),
                llamado.getEstado(),
                llamado.getAtendidoPorNombre(),
                llamado.getAtendidoEn(),
                segundos);
    }
}
