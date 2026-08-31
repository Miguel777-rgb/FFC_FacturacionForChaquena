package com.chaquena.backend_logistica.shared.dto;

import lombok.*;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envoltura de paginacion estable para el frontend. Evita exponer la
 * serializacion interna de {@link Page}, que cambia entre versiones de Spring.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponseDto<T> {

    private List<T> contenido;
    private int pagina;
    private int tamano;
    private long totalElementos;
    private int totalPaginas;
    private boolean primera;
    private boolean ultima;

    public static <T> PageResponseDto<T> de(Page<T> page) {
        return PageResponseDto.<T>builder()
                .contenido(page.getContent())
                .pagina(page.getNumber())
                .tamano(page.getSize())
                .totalElementos(page.getTotalElements())
                .totalPaginas(page.getTotalPages())
                .primera(page.isFirst())
                .ultima(page.isLast())
                .build();
    }

    public static <E, T> PageResponseDto<T> de(Page<E> page, Function<E, T> mapper) {
        return PageResponseDto.<T>builder()
                .contenido(page.getContent().stream().map(mapper).toList())
                .pagina(page.getNumber())
                .tamano(page.getSize())
                .totalElementos(page.getTotalElements())
                .totalPaginas(page.getTotalPages())
                .primera(page.isFirst())
                .ultima(page.isLast())
                .build();
    }
}
