package com.chaquena.backend_logistica.cocina.dto;

import java.util.List;

/**
 * Todo lo que viaja por el WebSocket del llamado, con un {@code tipo} que dice
 * que hacer con el resto:
 *
 * <ul>
 *   <li>{@code estado}: la foto inicial al suscribirse</li>
 *   <li>{@code llamado}: cocina llamo (o volvio a llamar)</li>
 *   <li>{@code atendido}: un mozo dijo «Voy»; el aviso desaparece de los demas</li>
 *   <li>{@code cerrado}: la comanda salio del pase sin respuesta</li>
 *   <li>{@code presencia}: cambio el numero de mozos conectados</li>
 *   <li>{@code error}: solo a quien lo provoco</li>
 * </ul>
 */
public record AvisoLlamadoDto(String tipo, List<LlamadoCocinaDto> llamados, Integer mozosConectados,
        String mensaje) {

    public static AvisoLlamadoDto estado(List<LlamadoCocinaDto> llamados, int mozosConectados) {
        return new AvisoLlamadoDto("estado", llamados, mozosConectados, null);
    }

    public static AvisoLlamadoDto de(String tipo, LlamadoCocinaDto llamado, Integer mozosConectados) {
        return new AvisoLlamadoDto(tipo, List.of(llamado), mozosConectados, null);
    }

    public static AvisoLlamadoDto cerrados(List<LlamadoCocinaDto> llamados) {
        return new AvisoLlamadoDto("cerrado", llamados, null, null);
    }

    public static AvisoLlamadoDto presencia(int mozosConectados) {
        return new AvisoLlamadoDto("presencia", List.of(), mozosConectados, null);
    }

    public static AvisoLlamadoDto error(String mensaje) {
        return new AvisoLlamadoDto("error", List.of(), null, mensaje);
    }
}
