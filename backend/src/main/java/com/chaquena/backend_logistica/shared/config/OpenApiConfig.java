package com.chaquena.backend_logistica.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrato OpenAPI para que el frontend Angular genere su cliente HTTP en
 * lugar de leer el codigo Java. Disponible en /swagger-ui.html y /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    public OpenAPI apiLogistica() {
        return new OpenAPI()
                .info(new Info()
                        .title("Backend Logistica - Chaquena")
                        .version("v1")
                        .description("API de POS, inventario, comandas, cocina, despacho y caja "
                                + "del sistema de gestion gastronomica Chaquena."))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    /**
     * Declara las respuestas como {@code application/json}.
     *
     * <p>Ningun controlador especifica {@code produces}, asi que springdoc las
     * documentaba como {@code *&#47;*}. Eso no era cosmetico: el generador de
     * TypeScript pregunta si el tipo declarado es JSON para elegir el
     * {@code responseType} de Angular, {@code *&#47;*} no lo es, y el cliente
     * quedaba pidiendo {@code blob}. Con blob, Angular no parsea el cuerpo de
     * las respuestas de error, de modo que el {@code message} que redacta
     * GlobalExceptionHandler llegaba al navegador como un Blob opaco y ninguna
     * pantalla podia leerlo: todas caian en su texto generico.
     *
     * <p>Se arregla aqui, en un solo punto, y no repitiendo {@code produces} en
     * los 24 controladores. El servidor ya devuelve JSON via Jackson; lo unico
     * que faltaba era decirlo en el contrato.
     */
    @Bean
    public OpenApiCustomizer respuestasComoJson() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(ruta ->
                    ruta.readOperations().forEach(operacion -> {
                        if (operacion.getResponses() == null) {
                            return;
                        }
                        operacion.getResponses().values().forEach(respuesta -> {
                            Content contenido = respuesta.getContent();
                            if (contenido == null) {
                                return;
                            }
                            MediaType comodin = contenido.remove("*/*");
                            if (comodin != null) {
                                contenido.addMediaType("application/json", comodin);
                            }
                        });
                    }));
        };
    }
}
