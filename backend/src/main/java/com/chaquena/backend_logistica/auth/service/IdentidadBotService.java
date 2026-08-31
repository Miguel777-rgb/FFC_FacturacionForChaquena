package com.chaquena.backend_logistica.auth.service;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Ata una cuenta de Discord a un trabajador ya dado de alta.
 *
 * <p>Es el punto donde el cambio de proveedor deja de ser cosmetico. Con
 * WhatsApp la identidad venia sola: Meta entrega el numero de quien escribe y
 * ese numero ya estaba en {@code personas.celular}, asi que el bot reconocia al
 * trabajador sin que nadie hiciera nada. Discord no entrega el correo de quien
 * escribe —es privado y solo se obtiene con OAuth y consentimiento explicito—,
 * sino un identificador numerico de cuenta que la base no ha visto nunca.
 *
 * <p>Por eso hace falta un paso de vinculacion, una sola vez por persona: el
 * trabajador escribe {@code /vincular} con el correo con el que esta en la
 * nomina y el bot guarda su snowflake. La comprobacion es la misma que gobierna
 * el inicio de sesion con Google: el correo tiene que corresponder a un
 * trabajador activo, porque si no, cualquiera con una cuenta de Discord
 * entraria a mover stock.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdentidadBotService {

    private final TrabajadorRepository trabajadorRepository;

    /**
     * Trabajador detras de un remitente, sea cual sea el proveedor.
     *
     * <p>Se prueban las dos llaves porque los formatos no se solapan: un
     * snowflake de Discord es un numero de 17 a 20 digitos y un celular
     * peruano tiene nueve, con o sin prefijo. Buscar por las dos deja que los
     * servicios de bot ignoren por completo de donde llego el mensaje, que es
     * justamente lo que permite cambiar de proveedor sin tocarlos.
     */
    @Transactional(readOnly = true)
    public Optional<Trabajador> trabajadorDe(String remitenteId) {
        if (remitenteId == null || remitenteId.isBlank()) {
            return Optional.empty();
        }
        return trabajadorRepository.findByDiscordUserId(remitenteId)
                .or(() -> trabajadorRepository.findByPersonaCelular(remitenteId));
    }

    /**
     * Trabajador activo detras del remitente, con el motivo del rechazo cuando
     * no lo hay. Los tres bots internos empiezan por aqui, asi que la regla de
     * quien puede operar por bot se escribe una sola vez.
     *
     * @throws ConflictoException si no esta vinculado, esta de baja o es
     *                            administrador
     */
    @Transactional(readOnly = true)
    public Trabajador exigirTrabajador(String remitenteId) {
        Trabajador trabajador = trabajadorDe(remitenteId).orElseThrow(() -> new ConflictoException(
                "⛔ Tu cuenta no esta vinculada a ningun trabajador.\n"
                        + "Usa `/vincular correo:tu.correo@gmail.com` con el correo con el que estas dado de alta."));

        if (trabajador.getActivo() == null || !trabajador.getActivo()) {
            throw new ConflictoException("⛔ Tu cuenta de trabajador esta inactiva.");
        }

        // Un administrador tiene el tablero web entero: dejarle mover stock por
        // chat solo sirve para que queden movimientos sin la trazabilidad que si
        // deja el panel.
        if (trabajador.getCargo() != null && "ROLE_ADMIN".equalsIgnoreCase(trabajador.getCargo().getNombre())) {
            throw new ConflictoException(
                    "⛔ Los administradores gestionan el sistema desde el dashboard web, no por el bot.");
        }

        return trabajador;
    }

    /**
     * Vincula la cuenta y devuelve el trabajador.
     *
     * @throws ConflictoException si el correo no esta en la nomina, el
     *                            trabajador esta de baja, o la cuenta de
     *                            Discord ya pertenece a otra persona
     */
    @Transactional
    public Trabajador vincular(String discordUserId, String discordTag, String correo) {
        String normalizado = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);

        Trabajador trabajador = trabajadorRepository.findByCorreoIgnoreCase(normalizado)
                .orElseThrow(() -> new ConflictoException(
                        "El correo " + normalizado + " no corresponde a ningun trabajador dado de alta. "
                                + "Pideselo al administrador antes de usar el bot."));

        if (trabajador.getActivo() == null || !trabajador.getActivo()) {
            throw new ConflictoException("El trabajador " + normalizado + " esta dado de baja.");
        }

        // Una cuenta de Discord no puede representar a dos personas: el kardex
        // acabaria atribuyendo movimientos a quien no los hizo.
        Optional<Trabajador> yaVinculado = trabajadorRepository.findByDiscordUserId(discordUserId);
        if (yaVinculado.isPresent() && !yaVinculado.get().getId().equals(trabajador.getId())) {
            throw new ConflictoException("Esta cuenta de Discord ya esta vinculada a "
                    + yaVinculado.get().getUsername() + ". Pide al administrador que la libere.");
        }

        trabajador.setDiscordUserId(discordUserId);
        trabajador.setModifiedBy("BOT_DISCORD");
        trabajadorRepository.save(trabajador);

        log.info("Cuenta de Discord {} ({}) vinculada al trabajador {}.",
                discordUserId, discordTag, trabajador.getUsername());
        return trabajador;
    }
}
