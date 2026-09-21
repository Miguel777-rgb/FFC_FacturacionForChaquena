package com.chaquena.backend_logistica.auth.service.impl;

import com.chaquena.backend_logistica.auth.correo.TextoRecuperacion;
import com.chaquena.backend_logistica.auth.domain.TokenRecuperacion;
import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.repository.TokenRecuperacionRepository;
import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.auth.service.RecuperacionService;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * El olvido de contrasena, de punta a punta.
 *
 * <p>Tres decisiones gobiernan esta clase:
 *
 * <ol>
 *   <li><b>Pedirlo nunca confirma nada.</b> El controlador responde el mismo
 *       texto exista o no el correo. Si dijera «ese correo no esta registrado»,
 *       el formulario se convertiria en un comprobador de quien trabaja aqui:
 *       basta una lista de correos y un rato para saber cuales son del local.</li>
 *   <li><b>Lo que se guarda es la huella, no el token.</b> El token viaja en el
 *       enlace y no queda escrito en ninguna parte del servidor; en la tabla
 *       esta su SHA-256. Un volcado de la base no sirve para entrar.</li>
 *   <li><b>Un enlace anula al anterior.</b> Pedir otro invalida los que
 *       quedaran vivos, de modo que solo el ultimo abre la puerta.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecuperacionServiceImpl implements RecuperacionService {

    /** La misma que valida el alta, en la pantalla y en el DTO del registro. */
    private static final Pattern CONTRASENA =
            Pattern.compile("(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}");

    /** 32 bytes de aleatoriedad: la fuerza bruta sobre el enlace no es una via. */
    private static final int BYTES_TOKEN = 32;

    private final TrabajadorRepository trabajadorRepository;
    private final TokenRecuperacionRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender correo;

    private final SecureRandom azar = new SecureRandom();

    @Value("${app.recuperacion.url-base}")
    private String urlBase;

    @Value("${app.recuperacion.minutos:30}")
    private long minutos;

    /** Peticiones por persona y hora. Sin freno, el boton es un cañon de correos. */
    @Value("${app.recuperacion.max-por-hora:3}")
    private int maxPorHora;

    @Value("${app.correo.remitente:no-responder@chaquena.pe}")
    private String remitente;

    @Override
    @Transactional
    public void solicitar(String correoPedido, Locale idioma) {
        String limpio = correoPedido == null ? "" : correoPedido.trim();
        if (limpio.isEmpty()) {
            return;
        }

        Optional<Trabajador> quiza = trabajadorRepository.findByCorreoIgnoreCase(limpio)
                .filter(t -> Boolean.TRUE.equals(t.getActivo()));
        if (quiza.isEmpty()) {
            // Ni excepcion ni registro con el correo: el que se equivoca de
            // direccion no deja rastro de un dato que no es suyo.
            log.info("Recuperacion pedida para un correo que no abre ninguna cuenta activa.");
            return;
        }

        Trabajador trabajador = quiza.get();
        Instant ahora = Instant.now();
        if (tokenRepository.countByTrabajadorAndDateCreatedAfter(
                trabajador, ahora.minus(1, ChronoUnit.HOURS)) >= maxPorHora) {
            log.warn("Recuperacion frenada: {} pidio mas de {} enlaces en una hora.",
                    trabajador.getUsername(), maxPorHora);
            return;
        }

        tokenRepository.anularLosDe(trabajador, ahora);

        byte[] crudo = new byte[BYTES_TOKEN];
        azar.nextBytes(crudo);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(crudo);

        tokenRepository.save(TokenRecuperacion.builder()
                .trabajador(trabajador)
                .huella(huellaDe(token))
                .expiraEn(ahora.plus(minutos, ChronoUnit.MINUTES))
                .build());

        enviar(trabajador, token, idioma);
    }

    @Override
    @Transactional
    public void restablecer(String token, String contrasenaNueva) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Falta el enlace de recuperacion.");
        }
        if (contrasenaNueva == null || !CONTRASENA.matcher(contrasenaNueva).matches()) {
            throw new IllegalArgumentException(
                    "La contrasena lleva ocho caracteres con mayuscula, minuscula y cifra.");
        }

        TokenRecuperacion guardado = tokenRepository.findByHuella(huellaDe(token))
                // No se distingue «no existe» de «mal escrito»: los dos son lo mismo
                // para quien llega con un enlace que no vale.
                .orElseThrow(() -> new ConflictoException(
                        "Este enlace no es valido. Pide uno nuevo."));

        Instant ahora = Instant.now();
        if (!guardado.vigente(ahora)) {
            throw new ConflictoException(guardado.getUsadoEn() != null
                    ? "Este enlace ya se uso. Pide uno nuevo."
                    : "Este enlace caduco. Pide uno nuevo.");
        }

        Trabajador trabajador = guardado.getTrabajador();
        if (!Boolean.TRUE.equals(trabajador.getActivo())) {
            throw new ConflictoException("Esta cuenta esta dada de baja.");
        }

        trabajador.setPasswordHash(passwordEncoder.encode(contrasenaNueva));
        trabajador.setModifiedBy("RECUPERACION");
        trabajadorRepository.save(trabajador);

        guardado.setUsadoEn(ahora);
        guardado.setModifiedBy("RECUPERACION");
        tokenRepository.save(guardado);
        log.info("Contrasena restablecida por enlace para {}.", trabajador.getUsername());
    }

    /**
     * El envio no puede tumbar la peticion: si el servidor de correo no responde,
     * quien pidio el enlace vera el mismo mensaje de siempre y el token quedara
     * escrito sin usar, que caduca solo. Enterarse de que el correo no sale es
     * trabajo del registro, no de la pantalla.
     */
    private void enviar(Trabajador trabajador, String token, Locale idioma) {
        TextoRecuperacion texto = TextoRecuperacion.de(idioma);
        String enlace = urlBase.replaceAll("/+$", "") + "/restablecer?token=" + token;
        String nombre = (trabajador.getNombres() == null ? "" : trabajador.getNombres()).trim();

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(trabajador.getCorreo());
        mensaje.setSubject(texto.asunto());
        mensaje.setText(texto.cuerpo(nombre.isEmpty() ? trabajador.getUsername() : nombre,
                enlace, minutos));

        try {
            correo.send(mensaje);
            log.info("Enlace de recuperacion enviado a {}.", trabajador.getUsername());
        } catch (Exception e) {
            log.error("No se pudo enviar el enlace de recuperacion a {}: {}",
                    trabajador.getUsername(), e.getMessage());
        }
    }

    private static String huellaDe(String token) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda maquina virtual de Java.
            throw new IllegalStateException("Sin SHA-256 no hay recuperacion posible", e);
        }
    }
}
