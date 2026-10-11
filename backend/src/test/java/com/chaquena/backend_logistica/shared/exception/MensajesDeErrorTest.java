package com.chaquena.backend_logistica.shared.exception;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los errores del backend en castellano, ingles y portugues, con la misma
 * configuracion que arma Spring Boot desde {@code spring.messages.*}.
 */
class MensajesDeErrorTest {

    private static final List<Locale> IDIOMAS = List.of(Locale.of("es"), Locale.ENGLISH, Locale.of("pt"));

    private final ResourceBundleMessageSource mensajes = mensajes();
    private final GlobalExceptionHandler manejador = new GlobalExceptionHandler(mensajes);

    static ResourceBundleMessageSource mensajes() {
        ResourceBundleMessageSource fuente = new ResourceBundleMessageSource();
        fuente.setBasename("messages");
        fuente.setDefaultEncoding(StandardCharsets.UTF_8.name());
        fuente.setFallbackToSystemLocale(false);
        return fuente;
    }

    @AfterEach
    void limpiar() {
        LocaleContextHolder.resetLocaleContext();
    }

    /** El equivalente del {@code Record<ClaveI18n, string>} del frontend: ninguna clave suelta. */
    @Test
    void cadaClaveExisteEnLosTresIdiomas() throws IOException {
        Set<String> base = claves("messages.properties");

        assertThat(base).hasSize(43);
        assertThat(claves("messages_en.properties")).isEqualTo(base);
        assertThat(claves("messages_pt.properties")).isEqualTo(base);
    }

    @Test
    void elManejadorRespondeEnElIdiomaDeLaPeticion() {
        assertThat(accesoDenegadoEn(Locale.ENGLISH))
                .containsExactly("Access denied", "Your role does not have permission for this operation.");
        assertThat(accesoDenegadoEn(Locale.of("pt", "BR")))
                .containsExactly("Acesso negado", "Seu cargo não tem permissão para esta operação.");
        assertThat(accesoDenegadoEn(Locale.of("es", "PE")))
                .containsExactly("Acceso denegado", "Tu cargo no tiene permiso para esta operación.");
    }

    /** Un idioma sin traducir cae en castellano, no en el idioma de la JVM. */
    @Test
    void unIdiomaSinTraducirCaeEnCastellano() {
        assertThat(accesoDenegadoEn(Locale.FRENCH))
                .containsExactly("Acceso denegado", "Tu cargo no tiene permiso para esta operación.");
    }

    @Test
    void losArgumentosSeSustituyenYLosApostrofosSalenSencillos() {
        Object[] argumentos = {"abc", "id", "UUID"};

        assertThat(mensajes.getMessage("error.mensaje.parametroInvalido", argumentos, Locale.of("es")))
                .isEqualTo("El valor 'abc' no sirve para 'id': se esperaba UUID.");
        assertThat(mensajes.getMessage("error.mensaje.parametroInvalido", argumentos, Locale.ENGLISH))
                .isEqualTo("The value 'abc' is not valid for 'id': expected UUID.");
        assertThat(mensajes.getMessage("trabajador.duplicado.dni", new Object[] {"70123456"}, Locale.of("pt")))
                .isEqualTo("Já existe uma pessoa cadastrada com o DNI 70123456.");
    }

    /**
     * Como en produccion: los textos con {0} pasan por MessageFormat y los demas salen tal cual. Ninguno
     * deja un hueco sin llenar ni un apostrofo doble a la vista.
     */
    @Test
    void ningunTextoQuedaAMedias() throws IOException {
        Properties base = propiedades("messages.properties");
        for (Locale idioma : IDIOMAS) {
            for (String clave : base.stringPropertyNames()) {
                Object[] argumentos = base.getProperty(clave).contains("{0}") ? new Object[] {"x", "y", "z"} : null;
                String texto = mensajes.getMessage(clave, argumentos, idioma);
                assertThat(texto).as("%s en %s", clave, idioma).doesNotContain("{", "}", "''");
            }
        }
    }

    private List<String> accesoDenegadoEn(Locale idioma) {
        LocaleContextHolder.setLocale(idioma);
        ErrorResponseDto cuerpo = manejador
                .accesoDenegado(new AccessDeniedException("sin permiso"), new MockHttpServletRequest())
                .getBody();
        assertThat(cuerpo).isNotNull();
        return List.of(cuerpo.getError(), cuerpo.getMessage());
    }

    private static Set<String> claves(String archivo) throws IOException {
        return propiedades(archivo).stringPropertyNames();
    }

    private static Properties propiedades(String archivo) throws IOException {
        Properties propiedades = new Properties();
        try (InputStream entrada = MensajesDeErrorTest.class.getClassLoader().getResourceAsStream(archivo)) {
            assertThat(entrada).as(archivo).isNotNull();
            propiedades.load(new InputStreamReader(entrada, StandardCharsets.UTF_8));
        }
        return propiedades;
    }
}
