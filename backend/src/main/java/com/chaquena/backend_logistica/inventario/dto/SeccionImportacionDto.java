package com.chaquena.backend_logistica.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * A que seccion van los platillos: una que ya existe ({@code categoriaId}) o una
 * nueva con {@code nombreNueva}. Si llegan las dos, manda la existente.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeccionImportacionDto {

    private Integer categoriaId;

    @Size(max = 100, message = "El nombre de la seccion admite hasta 100 caracteres")
    private String nombreNueva;

    @Valid
    private List<PlatilloImportacionDto> platillos;
}
