package com.chaquena.backend_logistica.clientes.dto;

import com.chaquena.backend_logistica.clientes.domain.Empresa;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpresaResponseDto {

    private UUID id;
    private String ruc;
    private String razonSocial;
    private String celular;
    private String direccionFiscal;

    public static EmpresaResponseDto fromEntity(Empresa e) {
        return EmpresaResponseDto.builder()
                .id(e.getId())
                .ruc(e.getRuc())
                .razonSocial(e.getRazonSocial())
                .celular(e.getCelular())
                .direccionFiscal(e.getDireccionFiscal())
                .build();
    }
}
