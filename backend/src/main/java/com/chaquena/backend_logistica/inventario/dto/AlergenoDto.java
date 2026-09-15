package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.Alergeno;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/** Alta, edicion y lectura de un alergeno. Darlo de baja va por su propio endpoint. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlergenoDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Integer id;

    @NotBlank(message = "El alergeno necesita un nombre")
    @Size(max = 60, message = "El nombre admite hasta 60 caracteres")
    private String nombre;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Boolean activo;

    public static AlergenoDto fromEntity(Alergeno a) {
        return AlergenoDto.builder()
                .id(a.getId())
                .nombre(a.getNombre())
                .activo(a.getActivo())
                .build();
    }
}
