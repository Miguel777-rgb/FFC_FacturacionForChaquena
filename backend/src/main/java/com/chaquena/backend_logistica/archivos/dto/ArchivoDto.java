package com.chaquena.backend_logistica.archivos.dto;

import com.chaquena.backend_logistica.archivos.domain.Archivo;
import lombok.*;

import java.util.UUID;

/** Lo que devuelve una subida: el id que luego se cuelga del platillo o del local. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArchivoDto {

    private UUID id;
    private String tipoContenido;
    private Long tamanoBytes;

    public static ArchivoDto fromEntity(Archivo archivo) {
        return ArchivoDto.builder()
                .id(archivo.getId())
                .tipoContenido(archivo.getTipoContenido())
                .tamanoBytes(archivo.getTamanoBytes())
                .build();
    }
}
