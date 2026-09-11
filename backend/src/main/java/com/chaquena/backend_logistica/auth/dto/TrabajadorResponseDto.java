package com.chaquena.backend_logistica.auth.dto;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrabajadorResponseDto {

    private UUID id;
    private String dni;
    private String nombres;
    private String apellidos;
    private String correo;
    private String celular;
    private String username;
    /**
     * Id del cargo, no solo su nombre: la pantalla de personal necesita
     * casar cada trabajador con su fila del catalogo de cargos para saber
     * que roles lleva, y hacerlo por nombre se rompe en cuanto un cargo se
     * renombra.
     */
    private Integer cargoId;
    private String cargoNombre;
    private Boolean activo;

    public static TrabajadorResponseDto fromEntity(Trabajador t) {
        return TrabajadorResponseDto.builder()
                .id(t.getId())
                .dni(t.getDni())
                .nombres(t.getNombres())
                .apellidos(t.getApellidos())
                .correo(t.getCorreo())
                .celular(t.getCelular())
                .username(t.getUsername())
                .cargoId(t.getCargo() != null ? t.getCargo().getId() : null)
                .cargoNombre(t.getCargo() != null ? t.getCargo().getNombre() : null)
                .activo(t.getActivo())
                .build();
    }
}