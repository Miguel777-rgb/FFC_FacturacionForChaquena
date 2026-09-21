package com.chaquena.backend_logistica.auth.controller;

import com.chaquena.backend_logistica.auth.dto.RestablecerPasswordRequestDto;
import com.chaquena.backend_logistica.auth.dto.SolicitarRecuperacionRequestDto;
import com.chaquena.backend_logistica.auth.service.RecuperacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Recuperar la contrasena olvidada. Los dos endpoints son publicos —cuelgan de
 * {@code /api/v1/auth/**}, que SecurityConfig ya deja pasar— porque quien los
 * usa no puede iniciar sesion, que es justamente el problema.
 */
@RestController
@RequestMapping("/api/v1/auth/recuperacion")
@RequiredArgsConstructor
@Tag(name = "Autenticacion - Recuperacion", description = "Restablecer la contrasena por correo")
public class RecuperacionController {

    private final RecuperacionService recuperacionService;

    /**
     * Responde 202 y el mismo texto exista o no el correo. Decir «ese correo no
     * esta registrado» convertiria este formulario en un comprobador de quien
     * tiene cuenta en el sistema.
     */
    @PostMapping
    @Operation(operationId = "solicitarRecuperacion",
            summary = "Pedir el enlace para restablecer la contrasena",
            description = "Contesta siempre lo mismo, exista o no el correo. El enlace caduca y "
                    + "solo sirve una vez; pedir otro anula el anterior.")
    public ResponseEntity<Void> solicitar(
            @Valid @RequestBody SolicitarRecuperacionRequestDto peticion,
            @RequestHeader(name = "Accept-Language", required = false) String idioma) {
        recuperacionService.solicitar(peticion.getCorreo(), locale(idioma));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/confirmacion")
    @Operation(operationId = "restablecerPasswordConToken",
            summary = "Poner la contrasena nueva con el token del enlace",
            description = "409 si el enlace caduco, ya se uso o no existe.")
    public ResponseEntity<Void> restablecer(
            @Valid @RequestBody RestablecerPasswordRequestDto peticion) {
        recuperacionService.restablecer(peticion.getToken(), peticion.getPassword());
        return ResponseEntity.noContent().build();
    }

    /** «pt-BR,pt;q=0.9» es portugues; lo que no se entienda, espanol. */
    private static Locale locale(String cabecera) {
        if (cabecera == null || cabecera.isBlank()) {
            return Locale.of("es");
        }
        String primero = cabecera.split(",")[0].split(";")[0].trim();
        return primero.isEmpty() ? Locale.of("es") : Locale.forLanguageTag(primero);
    }
}
