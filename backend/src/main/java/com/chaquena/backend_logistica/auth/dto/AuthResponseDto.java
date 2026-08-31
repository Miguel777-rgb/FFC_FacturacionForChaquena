package com.chaquena.backend_logistica.auth.dto;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDto {

    private UUID id;
    private String dni;
    private String nombres;
    private String apellidos;
    private String correo;
    private String celular;
    private String username;
    private String cargo;
    private String token;
    private String mensaje;

    public static AuthResponseDto fromEntity(Trabajador trabajador, String token, String mensaje) {
        return AuthResponseDto.builder()
                .id(trabajador.getId())
                .dni(trabajador.getDni())
                .nombres(trabajador.getNombres())
                .apellidos(trabajador.getApellidos())
                .correo(trabajador.getCorreo())
                .celular(trabajador.getCelular())
                .username(trabajador.getUsername())
                .cargo(trabajador.getCargo() != null ? trabajador.getCargo().getNombre() : null)
                .token(token)
                .mensaje(mensaje)
                .build();
    }
}
