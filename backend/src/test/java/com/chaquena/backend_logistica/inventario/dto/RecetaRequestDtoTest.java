package com.chaquena.backend_logistica.inventario.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un paso de la preparacion tiene que decir algo y caber en su columna. Que
 * no lleguen pasos no es un error: es no tocarlos.
 */
class RecetaRequestDtoTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void abrir() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrar() {
        fabrica.close();
    }

    private static RecetaRequestDto conPasos(List<String> pasos) {
        return RecetaRequestDto.builder()
                .insumos(List.of(RecetaItemDto.builder()
                        .insumoId(UUID.randomUUID()).cantidadRequerida(new BigDecimal("0.250")).build()))
                .pasos(pasos)
                .build();
    }

    private static Set<String> errores(RecetaRequestDto dto) {
        return validador.validate(dto).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void sinPasosOConPasosEscritosNoHayErrores() {
        assertThat(errores(conPasos(null))).isEmpty();
        assertThat(errores(conPasos(List.of()))).isEmpty();
        assertThat(errores(conPasos(List.of("Freír la papa.", "Servir con arroz.")))).isEmpty();
    }

    @Test
    void unPasoEnBlancoSeRechaza() {
        assertThat(errores(conPasos(List.of("Freír la papa.", "   "))))
                .containsExactly("Un paso de la preparacion no puede ir vacio");
    }

    @Test
    void unPasoQueNoCabeEnSuColumnaSeRechaza() {
        assertThat(errores(conPasos(List.of("a".repeat(500))))).isEmpty();
        assertThat(errores(conPasos(List.of("a".repeat(501)))))
                .containsExactly("Cada paso de la preparacion admite hasta 500 caracteres");
    }
}
