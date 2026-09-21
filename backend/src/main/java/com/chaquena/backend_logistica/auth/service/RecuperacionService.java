package com.chaquena.backend_logistica.auth.service;

import java.util.Locale;

/**
 * Recuperar la contrasena olvidada, sin pasar por un administrador.
 *
 * <p>Los dos pasos son publicos —quien los usa, por definicion, no puede entrar—
 * y por eso cada uno tiene su propia cautela: el primero responde siempre lo
 * mismo, exista o no el correo, y el segundo exige un token de un solo uso que
 * caduca.
 */
public interface RecuperacionService {

    /**
     * Manda el enlace si ese correo pertenece a una cuenta activa. No dice si lo
     * encontro: quien llama responde el mismo texto en los dos casos.
     */
    void solicitar(String correo, Locale idioma);

    /** Cambia la contrasena si el token esta vigente; si no, explica por que no. */
    void restablecer(String token, String contrasenaNueva);
}
