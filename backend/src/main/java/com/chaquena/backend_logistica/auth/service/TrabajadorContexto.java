package com.chaquena.backend_logistica.auth.service;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resuelve el trabajador detras del token para las operaciones que exigen
 * autoria real: quien movio el stock, quien tomo la comanda, quien cobro.
 */
@Component
@RequiredArgsConstructor
public class TrabajadorContexto {

    /**
     * Trabajador ficticio al que se atribuyen las operaciones que dispara el bot
     * de clientes. Un pedido que entra por el bot no tiene mozo detras, pero el
     * libro de inventario exige un autor para cada movimiento: antes que dejarlo
     * nulo o colgarselo a un administrador que no estuvo ahi, se registra al bot
     * con nombre propio. Lo siembra DatosInicialesSeeder y no puede iniciar
     * sesion.
     *
     * <p>El nombre no menciona el proveedor a proposito: cuando el canal paso de
     * WhatsApp a Discord, la comanda la siguio firmando el mismo bot y las
     * ventas anteriores no cambiaron de autor.
     */
    public static final String USERNAME_BOT_CLIENTES = "bot.clientes";

    private final TrabajadorRepository trabajadorRepository;

    public Optional<Trabajador> actual() {
        String usuario = UsuarioActual.username();
        if (UsuarioActual.SISTEMA.equals(usuario)) {
            return Optional.empty();
        }
        return trabajadorRepository.findByUsernameOrCorreo(usuario, usuario);
    }

    /**
     * Id del trabajador autenticado. Falla explicitamente en lugar de dejar el
     * movimiento sin autor, que es lo que hacia inutil la auditoria.
     */
    public UUID idActualObligatorio() {
        return actual()
                .map(Trabajador::getId)
                .orElseThrow(() -> new ConflictoException(
                        "La operacion requiere un trabajador autenticado y el token no corresponde "
                                + "a ninguno registrado."));
    }

    public UUID idActualONulo() {
        return actual().map(Trabajador::getId).orElse(null);
    }

    /**
     * Id del trabajador que representa al bot de clientes. Devuelve vacio si el
     * seeder esta apagado y la fila no existe, para que quien lo use decida si
     * eso es motivo de fallo.
     */
    public Optional<UUID> idDelBotDeClientes() {
        return trabajadorRepository.findByUsername(USERNAME_BOT_CLIENTES).map(Trabajador::getId);
    }
}
