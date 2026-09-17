package com.chaquena.backend_logistica.inventario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Un producto leido de la foto, antes de revisarlo. No se guarda nada: la
 * pantalla lo muestra en la tabla de revision y lo envia a importar si queda
 * marcado.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatilloLeidoDto {

    /** Nulo si no se pudo leer. */
    private String nombre;

    private String descripcion;

    /** Nulo si no se pudo leer. */
    private BigDecimal precio;

    /** La "V" del icono de vegetariano, cuando se detecto. */
    private boolean vegetariano;

    /** Falta el nombre o el precio, o la lectura fue de poca confianza. */
    private boolean dudoso;

    @Schema(description = "Recorte de la foto de donde salio, como data URL JPEG. Solo en las filas dudosas.")
    private String recorte;
}
