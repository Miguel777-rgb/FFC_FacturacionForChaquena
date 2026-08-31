package com.chaquena.backend_logistica.clientes.dto;

import com.chaquena.backend_logistica.clientes.domain.Cliente;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteResponseDto {

    private UUID id;
    private String dni;
    private String nombres;
    private String apellidos;
    private String nombreCompleto;
    private String correo;
    private String celular;
    private String direccionHabitual;
    private String tipoCliente;
    private Integer puntosFidelidad;
    private Integer scoreFraude;
    private Boolean bloqueadoPorFraude;

    public static ClienteResponseDto fromEntity(Cliente c) {
        return ClienteResponseDto.builder()
                .id(c.getId())
                .dni(c.getDni())
                .nombres(c.getNombres())
                .apellidos(c.getApellidos())
                .nombreCompleto((c.getNombres() + " " + c.getApellidos()).trim())
                .correo(c.getCorreo())
                .celular(c.getCelular())
                .direccionHabitual(c.getDireccionHabitual())
                .tipoCliente(c.getTipoCliente())
                .puntosFidelidad(c.getPuntosFidelidad())
                .scoreFraude(c.getScoreFraude())
                .bloqueadoPorFraude(c.getBloqueadoPorFraude())
                .build();
    }
}
