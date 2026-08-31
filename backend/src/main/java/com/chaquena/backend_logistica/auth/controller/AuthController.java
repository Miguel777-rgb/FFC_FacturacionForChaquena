package com.chaquena.backend_logistica.auth.controller;

import com.chaquena.backend_logistica.auth.dto.AuthResponseDto;
import com.chaquena.backend_logistica.auth.dto.BootstrapAdminRequestDto;
import com.chaquena.backend_logistica.auth.dto.LoginRequestDto;
import com.chaquena.backend_logistica.auth.dto.TrabajadorResponseDto;
import com.chaquena.backend_logistica.auth.service.AuthService;
import com.chaquena.backend_logistica.auth.service.TrabajadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticacion", description = "Login, Google OAuth y alta del primer administrador")
public class AuthController {

    private final AuthService authService;
    private final TrabajadorService trabajadorService;

    /**
     * Login/Registro con Google OAuth 2.0.
     * El Google Access Token se envía en el header Authorization (pestaña Auth de Postman).
     */
    @PostMapping("/google")
    public ResponseEntity<AuthResponseDto> autenticarConGoogle(
            @RequestHeader("Authorization") String authorizationHeader) {

        String googleAccessToken = authorizationHeader.replace("Bearer ", "").trim();
        AuthResponseDto response = authService.autenticarConGoogle(googleAccessToken);
        return ResponseEntity.ok(response);
    }

    /**
     * Login tradicional con usuario/correo y contraseña.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        AuthResponseDto response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Alta del primer administrador del sistema. Es el unico endpoint publico
     * que crea trabajadores y solo responde mientras la tabla esta vacia:
     * despues del primero, las altas pasan por POST /api/v1/trabajadores, que
     * exige un token de administrador.
     */
    @PostMapping("/bootstrap")
    @Operation(summary = "Crear el primer administrador (solo con la tabla de trabajadores vacia)")
    public ResponseEntity<TrabajadorResponseDto> bootstrap(
            @Valid @RequestBody BootstrapAdminRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(trabajadorService.bootstrapPrimerAdmin(request));
    }
}
