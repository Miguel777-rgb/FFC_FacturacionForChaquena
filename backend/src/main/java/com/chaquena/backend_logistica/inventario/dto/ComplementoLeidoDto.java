package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.TipoComplementoEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Un adicional que se suma a varios platos ("+5 con Chaufa"). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplementoLeidoDto {

    /** Nulo cuando la carta lo indica con un icono y no con texto. */
    private String nombre;

    private BigDecimal precio;

    private TipoComplementoEnum tipo;

    private boolean dudoso;

    @Schema(description = "Recorte de la foto de donde salio, como data URL JPEG. Solo si es dudoso.")
    private String recorte;
}
