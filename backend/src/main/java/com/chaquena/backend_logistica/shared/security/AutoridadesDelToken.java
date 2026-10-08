package com.chaquena.backend_logistica.shared.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Las autoridades que concede un JWT de este backend, a partir de los tres
 * niveles del modelo de seguridad: el cargo del trabajador, los roles que ese
 * cargo agrupa (tabla cargo_roles) y los permisos finos de cada rol (tabla
 * rol_permisos).
 *
 * <p>La usan el filtro HTTP y el interceptor del WebSocket, y vive en un solo
 * sitio porque es una regla de seguridad: si cada canal normalizara los roles a
 * su manera, el mismo token podria abrir en uno lo que el otro niega.
 */
public final class AutoridadesDelToken {

    private AutoridadesDelToken() {
    }

    public static List<SimpleGrantedAuthority> de(Claims claims) {
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
    private static List<String> listaDeClaims(Claims claims, String nombre) {
        Object valor = claims.get(nombre);
        if (valor instanceof List<?> lista) {
            return ((List<Object>) lista).stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return List.of();
    }

    /** Recorta, pasa a mayusculas y convierte espacios y guiones en guion bajo. */
    public static String normalizar(String valor) {
        return valor.trim().toUpperCase().replace(" ", "_").replace("-", "_");
    }
}
