package com.chaquena.backend_logistica.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las expresiones regulares del alta de un trabajador, del lado del servidor.
 *
 * La pantalla valida lo mismo mientras se escribe, pero esa validacion es
 * comodidad: un POST con curl no pasa por el formulario. Esta prueba fija que
 * el servidor rechaza por su cuenta lo que el navegador ya habria rechazado.
 */
class RegistrarTrabajadorRequestDtoTest {

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

    /** Un alta correcta, sobre la que cada prueba estropea un solo campo. */
    private static RegistrarTrabajadorRequestDto.RegistrarTrabajadorRequestDtoBuilder valido() {
        return RegistrarTrabajadorRequestDto.builder()
                .dni("70123456")
                .nombres("María José")
                .apellidos("García-López")
                .correo("mozo.uno@chaquena.pe")
                .celular("987654321")
                .cargoId(1)
                .username("mozo1")
                .password("Chaquena2001");
    }

    private static Set<String> camposConError(RegistrarTrabajadorRequestDto dto) {
        return validador.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void unAltaBienEscritaNoTieneNingunError() {
        assertThat(camposConError(valido().build())).isEmpty();
    }

    @Test
    void elCelularAdmiteElPrefijoDelPais() {
        assertThat(camposConError(valido().celular("51987654321").build())).isEmpty();
        assertThat(camposConError(valido().celular("+51 987654321").build())).isEmpty();
    }

    @Test
    void rechazaUnDniQueNoSeanOchoDigitos() {
        assertThat(camposConError(valido().dni("701234").build())).containsExactly("dni");
        assertThat(camposConError(valido().dni("7012345a").build())).containsExactly("dni");
    }

    @Test
    void rechazaUnFijoComoCelular() {
        assertThat(camposConError(valido().celular("014567890").build())).containsExactly("celular");
    }

    @Test
    void rechazaUnCorreoSinDominio() {
        assertThat(camposConError(valido().correo("sin-arroba.pe").build())).containsExactly("correo");
    }

    @Test
    void rechazaUnNombreConCifras() {
        assertThat(camposConError(valido().nombres("Mozo1").build())).containsExactly("nombres");
    }

    @Test
    void rechazaUnUsuarioConMayusculas() {
        assertThat(camposConError(valido().username("Mozo1").build())).containsExactly("username");
    }

    @Test
    void rechazaUnaContrasenaSinMayusculaOSinCifra() {
        assertThat(camposConError(valido().password("chaquena2001").build()))
                .containsExactly("password");
        assertThat(camposConError(valido().password("Chaquena").build())).containsExactly("password");
        assertThat(camposConError(valido().password("Abc1234").build())).containsExactly("password");
    }

    /** Con el MessageSource de la aplicacion, el mismo error sale en el idioma de la peticion. */
    @Test
    void elMensajeSaleEnElIdiomaDeLaPeticion() {
        ResourceBundleMessageSource mensajes = new ResourceBundleMessageSource();
        mensajes.setBasename("messages");
        mensajes.setDefaultEncoding("UTF-8");
        mensajes.setFallbackToSystemLocale(false);
        LocalValidatorFactoryBean conMensajes = new LocalValidatorFactoryBean();
        conMensajes.setValidationMessageSource(mensajes);
        conMensajes.afterPropertiesSet();
        RegistrarTrabajadorRequestDto dniCorto = valido().dni("701234").build();
        try {
            assertThat(mensajeEn(conMensajes, dniCorto, Locale.ENGLISH)).isEqualTo("The DNI must be eight digits.");
            assertThat(mensajeEn(conMensajes, dniCorto, Locale.of("pt", "BR"))).isEqualTo("O DNI deve ter oito dígitos.");
            assertThat(mensajeEn(conMensajes, dniCorto, Locale.of("es", "PE"))).isEqualTo("El DNI son ocho dígitos.");
        } finally {
            conMensajes.close();
            LocaleContextHolder.resetLocaleContext();
        }
    }

    private static String mensajeEn(Validator conMensajes, RegistrarTrabajadorRequestDto dto, Locale idioma) {
        LocaleContextHolder.setLocale(idioma);
        Set<ConstraintViolation<RegistrarTrabajadorRequestDto>> errores = conMensajes.validate(dto);
        assertThat(errores).hasSize(1);
        return errores.iterator().next().getMessage();
    }
}
