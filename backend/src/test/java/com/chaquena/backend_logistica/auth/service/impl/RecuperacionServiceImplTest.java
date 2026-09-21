package com.chaquena.backend_logistica.auth.service.impl;

import com.chaquena.backend_logistica.auth.domain.TokenRecuperacion;
import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.repository.TokenRecuperacionRepository;
import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El olvido de contrasena. Lo que se prueba aqui no es que el correo salga
 * —eso lo hace el servidor de correo— sino las tres cautelas del mecanismo: que
 * pedirlo no confirme quien tiene cuenta, que lo guardado sea la huella y no el
 * token, y que un enlace sirva una sola vez.
 */
@ExtendWith(MockitoExtension.class)
class RecuperacionServiceImplTest {

    @Mock
    private TrabajadorRepository trabajadorRepository;

    @Mock
    private TokenRecuperacionRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JavaMailSender correo;

    @InjectMocks
    private RecuperacionServiceImpl servicio;

    private Trabajador mozo;

    @BeforeEach
    void preparar() {
        ReflectionTestUtils.setField(servicio, "urlBase", "https://ffc.ejemplo/");
        ReflectionTestUtils.setField(servicio, "minutos", 30L);
        ReflectionTestUtils.setField(servicio, "maxPorHora", 3);
        ReflectionTestUtils.setField(servicio, "remitente", "no-responder@chaquena.pe");

        mozo = Trabajador.builder().username("mozo1").activo(true)
                .passwordHash("hash-viejo").build();
        mozo.setNombres("Rosa");
        mozo.setCorreo("rosa@chaquena.pe");
    }

    /** El token que viajo en el enlace, sacado del cuerpo del correo enviado. */
    private String tokenEnviado() {
        ArgumentCaptor<SimpleMailMessage> mensaje = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(correo).send(mensaje.capture());
        String cuerpo = mensaje.getValue().getText();
        int i = cuerpo.indexOf("?token=") + "?token=".length();
        return cuerpo.substring(i).split("\\s", 2)[0];
    }

    @Test
    void unCorreoQueNoAbreNingunaCuentaNoMandaNadaYNoSeQueja() {
        when(trabajadorRepository.findByCorreoIgnoreCase("nadie@ejemplo.pe"))
                .thenReturn(Optional.empty());

        servicio.solicitar("nadie@ejemplo.pe", Locale.of("es"));

        verify(correo, never()).send(any(SimpleMailMessage.class));
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void unaCuentaDadaDeBajaTampocoRecibeEnlace() {
        mozo.setActivo(false);
        when(trabajadorRepository.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.of(mozo));

        servicio.solicitar("rosa@chaquena.pe", Locale.of("es"));

        verify(correo, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void guardaLaHuellaYNoElToken() {
        when(trabajadorRepository.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.of(mozo));
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        servicio.solicitar("rosa@chaquena.pe", Locale.of("es"));

        ArgumentCaptor<TokenRecuperacion> guardado = ArgumentCaptor.forClass(TokenRecuperacion.class);
        verify(tokenRepository).save(guardado.capture());
        String token = tokenEnviado();

        assertThat(guardado.getValue().getHuella()).hasSize(64).isNotEqualTo(token);
        assertThat(guardado.getValue().getExpiraEn()).isAfter(Instant.now());
        // Pedir uno nuevo anula los anteriores.
        verify(tokenRepository).anularLosDe(any(), any());
    }

    @Test
    void elCorreoLlegaEnElIdiomaDeLaPantalla() {
        when(trabajadorRepository.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.of(mozo));
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        servicio.solicitar("rosa@chaquena.pe", Locale.of("en"));

        ArgumentCaptor<SimpleMailMessage> mensaje = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(correo).send(mensaje.capture());
        assertThat(mensaje.getValue().getSubject()).isEqualTo("Reset your Chaquena Logística password");
        assertThat(mensaje.getValue().getText()).contains("https://ffc.ejemplo/restablecer?token=");
    }

    @Test
    void masDeTresPeticionesEnUnaHoraNoMandanNada() {
        when(trabajadorRepository.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.of(mozo));
        when(tokenRepository.countByTrabajadorAndDateCreatedAfter(any(), any())).thenReturn(3L);

        servicio.solicitar("rosa@chaquena.pe", Locale.of("es"));

        verify(tokenRepository, never()).save(any());
        verify(correo, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void elEnlaceCambiaLaContrasenaYSeGastaAlUsarlo() {
        when(trabajadorRepository.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.of(mozo));
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        servicio.solicitar("rosa@chaquena.pe", Locale.of("es"));
        String token = tokenEnviado();

        ArgumentCaptor<TokenRecuperacion> guardado = ArgumentCaptor.forClass(TokenRecuperacion.class);
        verify(tokenRepository).save(guardado.capture());
        TokenRecuperacion vivo = guardado.getValue();
        when(tokenRepository.findByHuella(vivo.getHuella())).thenReturn(Optional.of(vivo));
        when(passwordEncoder.encode("Chaquena2026")).thenReturn("hash-nuevo");

        servicio.restablecer(token, "Chaquena2026");

        assertThat(mozo.getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(vivo.getUsadoEn()).isNotNull();
    }

    @Test
    void unEnlaceYaUsadoNoSirveDosVeces() {
        TokenRecuperacion usado = TokenRecuperacion.builder()
                .trabajador(mozo)
                .huella("da39a3ee5e6b4b0d3255bfef95601890afd80709")
                .expiraEn(Instant.now().plus(10, ChronoUnit.MINUTES))
                .usadoEn(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();
        when(tokenRepository.findByHuella(anyString())).thenReturn(Optional.of(usado));

        assertThatThrownBy(() -> servicio.restablecer("loQueSea", "Chaquena2026"))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("ya se uso");
    }

    @Test
    void unEnlaceCaducadoLoDiceYNoCambiaNada() {
        TokenRecuperacion viejo = TokenRecuperacion.builder()
                .trabajador(mozo)
                .huella("otra-huella")
                .expiraEn(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();
        when(tokenRepository.findByHuella(anyString())).thenReturn(Optional.of(viejo));

        assertThatThrownBy(() -> servicio.restablecer("loQueSea", "Chaquena2026"))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("caduco");
        assertThat(mozo.getPasswordHash()).isEqualTo("hash-viejo");
    }

    @Test
    void unEnlaceInventadoNoDiceSiExistioAlguien() {
        when(tokenRepository.findByHuella(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.restablecer("inventado", "Chaquena2026"))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("no es valido");
    }

    @Test
    void unaContrasenaFlojaSeRechazaAntesDeMirarElToken() {
        assertThatThrownBy(() -> servicio.restablecer("loQueSea", "chaquena"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayuscula");

        verify(tokenRepository, never()).findByHuella(anyString());
    }
}
