package com.chaquena.backend_logistica.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

/** Idioma por Accept-Language; sin cabecera, espanol y no el ingles del sistema del contenedor. */
@Configuration
public class IdiomaConfig {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolutor = new AcceptHeaderLocaleResolver();
        resolutor.setDefaultLocale(Locale.of("es", "PE"));
        return resolutor;
    }
}
