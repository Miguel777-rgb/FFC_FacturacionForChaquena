package com.chaquena.backend_logistica.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

/**
 * El idioma de una peticion sale de {@code Accept-Language}, que el frontend
 * manda siempre. Sin esa cabecera —curl, Postman, un bot— se asume espanol, que
 * es el idioma por defecto de la interfaz, y no el del sistema operativo del
 * contenedor, que suele ser ingles.
 */
@Configuration
public class IdiomaConfig {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolutor = new AcceptHeaderLocaleResolver();
        resolutor.setDefaultLocale(Locale.of("es", "PE"));
        return resolutor;
    }
}
