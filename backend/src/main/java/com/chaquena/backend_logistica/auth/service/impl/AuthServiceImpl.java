package com.chaquena.backend_logistica.auth.service.impl;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.dto.AuthResponseDto;
import com.chaquena.backend_logistica.auth.dto.LoginRequestDto;
import com.chaquena.backend_logistica.auth.repository.CargoRepository;
import com.chaquena.backend_logistica.auth.repository.RolPermisoRepository;
import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.auth.service.AuthService;
import com.chaquena.backend_logistica.shared.exception.AutenticacionFallidaException;
import com.chaquena.backend_logistica.shared.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    /** Endpoint de Google que si devuelve la audiencia (`aud`) del token. */
    private static final String GOOGLE_TOKENINFO = "https://oauth2.googleapis.com/tokeninfo";

    /** El mismo texto para las dos formas de fallar: ver el comentario en login(). */
    private static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos.";

    private final TrabajadorRepository trabajadorRepository;
    private final CargoRepository cargoRepository;
    private final RolPermisoRepository rolPermisoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private final RestClient restClient = RestClient.create();

    /**
     * Client id de la credencial OAuth de Google Cloud. Es la pieza que impide
     * que un token emitido para otra aplicacion sirva para entrar aqui. Sin el
     * configurado no se acepta ningun inicio de sesion con Google: fallar
     * cerrado es preferible a aceptar cualquier token del mundo.
     */
    @Value("${google.oauth2.client-id:}")
    private String googleClientId;

    /**
     * Inicio de sesion con Google.
     *
     * <p>Google verifica quien es la persona; este metodo decide si esa persona
     * puede entrar. Son dos preguntas distintas y las dos hay que hacerlas:
     *
     * <ol>
     *   <li><b>El token es para nosotros.</b> Se consulta {@code tokeninfo}, que
     *       devuelve la audiencia del token, y se contrasta con nuestro client
     *       id. Sin esta comprobacion valdria cualquier token de Google emitido
     *       para cualquier aplicacion del mundo.</li>
     *   <li><b>La persona ya trabaja aqui.</b> Un correo que no corresponde a
     *       ningun trabajador se rechaza. Antes se daba de alta solo, con lo que
     *       cualquiera con una cuenta de Google entraba a la nomina.</li>
     * </ol>
     *
     * <p>El alta de personal es responsabilidad de un administrador via
     * {@code POST /api/v1/trabajadores}; Google solo sirve para identificar a
     * quien ya figura.
     */
    @Override
    @Transactional(readOnly = true)
    public AuthResponseDto autenticarConGoogle(String googleAccessToken) {
        if (googleAccessToken == null || googleAccessToken.isBlank()) {
            throw new AutenticacionFallidaException("No se recibio ningun token de Google.");
        }
        if (googleClientId == null || googleClientId.isBlank()) {
            // Sin client id no se puede comprobar para quien se emitio el token,
            // y aceptarlo a ciegas es peor que no ofrecer el inicio con Google.
            log.error("google.oauth2.client-id sin configurar: se rechaza el inicio de sesion con Google.");
            throw new AutenticacionFallidaException(
                    "El inicio de sesion con Google no esta configurado en el servidor.");
        }

        Map<String, Object> tokenInfo = consultarTokenInfo(googleAccessToken);

        // La audiencia dice para que aplicacion se emitio el token. Si no es la
        // nuestra, el token es de un tercero y no vale aqui.
        String audiencia = (String) tokenInfo.get("aud");
        if (!googleClientId.equals(audiencia)) {
            log.warn("Token de Google rechazado: emitido para otra aplicacion (aud={}).", audiencia);
            throw new AutenticacionFallidaException(
                    "El token de Google fue emitido para otra aplicacion.");
        }

        String email = (String) tokenInfo.get("email");
        if (email == null || email.isBlank()) {
            throw new AutenticacionFallidaException(
                    "El token de Google no incluye el correo. Falta el permiso 'email'.");
        }

        // Google devuelve email_verified como cadena en tokeninfo, no como booleano,
        // y no siempre lo incluye: depende de los permisos concedidos. Se rechaza
        // solo cuando dice "false" de forma explicita. Tratar la ausencia como no
        // verificado dejaba fuera a gente con la cuenta correcta, y ademas con un
        // mensaje que apuntaba al sitio equivocado.
        //
        // La comprobacion es defensa en profundidad, no la puerta principal: el
        // token ya viene emitido por Google para esta aplicacion, y ademas el
        // correo tiene que coincidir con un trabajador dado de alta.
        Object correoVerificado = tokenInfo.get("email_verified");
        if (correoVerificado != null
                && "false".equalsIgnoreCase(String.valueOf(correoVerificado))) {
            throw new AutenticacionFallidaException(
                    "La cuenta de Google no tiene el correo verificado.");
        }
        if (correoVerificado == null) {
            log.debug("tokeninfo no devolvio email_verified para {}; se continua.", email);
        }

        Trabajador trabajador = trabajadorRepository.findByCorreo(email)
                .orElseThrow(() -> {
                    log.warn("Inicio con Google rechazado: {} no corresponde a ningun trabajador.", email);
                    return new AutenticacionFallidaException(
                            "El correo " + email + " no corresponde a ningun trabajador dado de alta. "
                                    + "Pide a un administrador que te registre.");
                });

        if (Boolean.FALSE.equals(trabajador.getActivo())) {
            log.warn("Inicio con Google rechazado: el trabajador {} esta dado de baja.", email);
            throw new AutenticacionFallidaException("Tu cuenta esta dada de baja.");
        }

        log.info("Trabajador autenticado via Google: {}", email);
        String backendJwt = generarJwtParaTrabajador(trabajador);
        return AuthResponseDto.fromEntity(trabajador, backendJwt, "Autenticación con Google exitosa");
    }

    /**
     * Pregunta a Google por el token. Se usa {@code tokeninfo} y no
     * {@code userinfo} porque solo el primero devuelve el campo {@code aud}:
     * {@code userinfo} contesta quien es el usuario, pero no para que
     * aplicacion se emitio el token, que es justo lo que hay que comprobar.
     */
    private Map<String, Object> consultarTokenInfo(String googleAccessToken) {
        Map<String, Object> tokenInfo;
        try {
            tokenInfo = restClient.get()
                    .uri(GOOGLE_TOKENINFO + "?access_token={t}", googleAccessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // Google responde 400 tanto para un token malformado como para uno
            // caducado, asi que no se puede distinguir un caso del otro.
            log.error("Google rechazo el token: {}", e.getMessage());
            throw new AutenticacionFallidaException("El token de Google es invalido o ha expirado.");
        }
        if (tokenInfo == null || tokenInfo.isEmpty()) {
            throw new AutenticacionFallidaException("Google no devolvio informacion del token.");
        }
        return tokenInfo;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDto login(LoginRequestDto request) {
        // Un unico mensaje para "no existe" y para "contrasena incorrecta". Antes
        // se distinguian, y eso permitia averiguar que correos estan dados de
        // alta probandolos uno a uno: el que existe responde distinto que el que
        // no. Ademas, a quien se equivoca no le sirve de nada saber cual de los
        // dos campos fallo; solo le sirve al que esta probando cuentas ajenas.
        Trabajador trabajador = trabajadorRepository.findByUsernameOrCorreo(
                request.getUsernameOrEmail(), request.getUsernameOrEmail())
                .orElseThrow(() -> new AutenticacionFallidaException(CREDENCIALES_INVALIDAS));

        if (!passwordEncoder.matches(request.getPassword(), trabajador.getPasswordHash())) {
            throw new AutenticacionFallidaException(CREDENCIALES_INVALIDAS);
        }

        // Dar de baja a alguien tiene que cerrarle las dos puertas. Sin esto, un
        // trabajador desactivado seguia entrando con usuario y contrasena
        // aunque la vía de Google ya se lo impidiera.
        if (Boolean.FALSE.equals(trabajador.getActivo())) {
            log.warn("Inicio de sesion rechazado: el trabajador {} esta dado de baja.",
                    trabajador.getUsername());
            throw new AutenticacionFallidaException("Tu cuenta esta dada de baja.");
        }

        String backendJwt = generarJwtParaTrabajador(trabajador);
        return AuthResponseDto.fromEntity(trabajador, backendJwt, "Inicio de sesión exitoso");
    }

    /**
     * Arma el token con los tres niveles del modelo de seguridad: el cargo, los
     * roles que ese cargo agrupa y los permisos finos de cada rol. Antes solo
     * viajaba el nombre del cargo, con lo que las tablas roles, permisos,
     * rol_permisos y cargo_roles no se usaban para nada.
     */
    private String generarJwtParaTrabajador(Trabajador trabajador) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", trabajador.getId().toString());
        claims.put("username", trabajador.getUsername());
        claims.put("nombres", trabajador.getNombres());
        claims.put("apellidos", trabajador.getApellidos());

        if (trabajador.getCargo() != null) {
            Integer cargoId = trabajador.getCargo().getId();
            claims.put("cargo", trabajador.getCargo().getNombre());
            claims.put("cargoId", cargoId);

            List<String> roles = cargoRepository.findConRolesById(cargoId)
                    .map(cargo -> cargo.getCargoRoles().stream()
                            .filter(cr -> cr.getRol() != null)
                            .map(cr -> cr.getRol().getNombre())
                            .distinct()
                            .toList())
                    .orElseGet(List::of);
            claims.put("roles", roles);
            claims.put("permisos", rolPermisoRepository.nombresPermisosPorCargo(cargoId));
        } else {
            claims.put("roles", List.of());
            claims.put("permisos", List.of());
        }

        return jwtService.generateToken(trabajador.getCorreo(), claims);
    }
}
