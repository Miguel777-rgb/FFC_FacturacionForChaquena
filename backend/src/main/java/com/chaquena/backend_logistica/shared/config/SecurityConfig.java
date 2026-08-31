package com.chaquena.backend_logistica.shared.config;

import com.chaquena.backend_logistica.shared.security.JwtAuthenticationFilter;
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

    /**
     * Origenes del frontend Angular. frontend-logistica va en el 81 y
     * frontend-facturacion en el 80; en desarrollo Angular sirve en el 4200.
     * Configurable con app.cors.allowed-origins.
     */
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

                        // Autenticacion y webhooks de Meta: publicos por definicion.
                        //
                        // Los bots de Discord no necesitan ninguna regla aqui: no
                        // reciben por HTTP, sino por el WebSocket que el backend
                        // abre hacia la pasarela. Estas rutas solo existen con
                        // app.mensajeria.proveedor=whatsapp, y se quedan porque
                        // ese adaptador sigue vivo.
                        .requestMatchers("/api/v1/auth", "/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/whatsapp", "/api/v1/whatsapp/**").permitAll()
                        .requestMatchers("/.well-known/jwks.json").permitAll()

                        // Contrato y sonda de salud.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
                                "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                        // El alta de trabajadores ya no es publica: antes cualquiera en
                        // internet podia darse de alta. El primer administrador se crea
                        // con POST /api/v1/auth/bootstrap, que solo funciona con la
                        // tabla vacia.
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
