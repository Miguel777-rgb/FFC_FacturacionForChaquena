package com.chaquena.backend_logistica.inventario.dto;

import com.chaquena.backend_logistica.inventario.domain.Proveedor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.UUID;

/** Alta, edicion y lectura de un proveedor. Activarlo o darlo de baja va por su propio endpoint. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProveedorDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @NotBlank(message = "El proveedor necesita un nombre")
    @Size(max = 120, message = "El nombre admite hasta 120 caracteres")
    private String nombre;

    @Pattern(regexp = "\\d{11}", message = "El RUC tiene 11 digitos")
    private String ruc;

    @Size(max = 120, message = "El contacto admite hasta 120 caracteres")
    private String contacto;

    @Size(max = 20, message = "El telefono admite hasta 20 caracteres")
    private String telefono;

    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 120, message = "El correo admite hasta 120 caracteres")
    private String correo;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Boolean activo;

    public static ProveedorDto fromEntity(Proveedor p) {
        return ProveedorDto.builder()
                .id(p.getId())
                .nombre(p.getNombre())
                .ruc(p.getRuc())
                .contacto(p.getContacto())
                .telefono(p.getTelefono())
                .correo(p.getCorreo())
                .activo(p.getActivo())
                .build();
    }
}
