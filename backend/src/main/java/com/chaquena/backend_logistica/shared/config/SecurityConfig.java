package com.chaquena.backend_logistica.shared.config;

import com.chaquena.backend_logistica.shared.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** Origenes del frontend: logistica en el 81, facturacion en el 80 y desarrollo en el 4200. */
    @Value("${app.cors.allowed-origins:http://localhost:81,http://localhost:80,http://localhost:4200}")
    private String origenesPermitidos;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Preflight CORS: el navegador lo manda sin cabecera Authorization.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // El cierre del stream SSE es un despacho asincrono que ya no pasa por
                        // el filtro JWT: la peticion original se autorizo al abrirse.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()

                        // Autenticacion y webhooks de Meta, publicos por definicion. Discord no
                        // necesita regla: los bots reciben por un WebSocket que abre el backend.
                        .requestMatchers("/api/v1/auth", "/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/whatsapp", "/api/v1/whatsapp/**").permitAll()
                        .requestMatchers("/.well-known/jwks.json").permitAll()

                        // Contrato y sonda de salud.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
                                "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                        // Saludo del WebSocket del llamado: entra sin token porque el navegador no
                        // deja ponerlo; el JWT viaja en la trama CONNECT y lo valida AutenticacionStomp.
                        .requestMatchers("/api/v1/ws", "/api/v1/ws/**").permitAll()

                        // Fotos de la carta y logo: un <img> no manda Authorization.
                        // Solo lectura y por UUID; subirlas sigue pidiendo sesion y cargo.
                        .requestMatchers(HttpMethod.GET, "/api/v1/archivos/*").permitAll()

                        // El alta de trabajadores ya no es publica; el primer administrador nace
                        // en POST /api/v1/auth/bootstrap.
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origenesPermitidos.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
