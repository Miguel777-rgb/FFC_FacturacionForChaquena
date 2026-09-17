package com.chaquena.backend_logistica.inventario.dto;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Las filas que quedaron marcadas en la revision, listas para guardar. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportacionCartaDto {

    @Valid
    private List<SeccionImportacionDto> secciones;

    @Valid
    private List<ComplementoImportacionDto> complementos;
}
