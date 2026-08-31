package com.chaquena.backend_logistica.shared.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/whatsapp")
                || path.startsWith("/.well-known")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();

        try {
            Claims claims = jwtService.validateToken(token);
            String subject = claims.getSubject();

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    subject, null, extraerAutoridades(claims));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.warn("Backend JWT invalido o expirado: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Arma las autoridades a partir de los tres niveles del modelo de seguridad:
     * el cargo del trabajador, los roles que ese cargo agrupa (tabla cargo_roles)
     * y los permisos finos de cada rol (tabla rol_permisos). Antes solo se usaba
     * el nombre del cargo, con lo que las tablas roles/permisos no servian
     * para nada.
     */
    private List<SimpleGrantedAuthority> extraerAutoridades(Claims claims) {
        List<SimpleGrantedAuthority> autoridades = new ArrayList<>();

        String cargo = claims.get("cargo", String.class);
        if (cargo != null && !cargo.isBlank()) {
            autoridades.add(new SimpleGrantedAuthority("ROLE_" + normalizar(cargo)));
        }

        for (String rol : listaDeClaims(claims, "roles")) {
            autoridades.add(new SimpleGrantedAuthority("ROLE_" + normalizar(rol)));
        }

        for (String permiso : listaDeClaims(claims, "permisos")) {
            autoridades.add(new SimpleGrantedAuthority(normalizar(permiso)));
        }

        return autoridades.stream().distinct().toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> listaDeClaims(Claims claims, String nombre) {
        Object valor = claims.get(nombre);
        if (valor instanceof List<?> lista) {
            return ((List<Object>) lista).stream()
                    .filter(java.util.Objects::nonNull)
                    .map(Object::toString)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return List.of();
    }

    private String normalizar(String valor) {
        return valor.trim().toUpperCase().replace(" ", "_").replace("-", "_");
    }
}
