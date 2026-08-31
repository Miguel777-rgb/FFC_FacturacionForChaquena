package com.chaquena.backend_logistica.pedidos.bot.service;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.service.IdentidadBotService;
import com.chaquena.backend_logistica.inventario.dto.PlatilloDisponibleDto;
import com.chaquena.backend_logistica.inventario.service.PlatilloService;
import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.domain.Mesa;
import com.chaquena.backend_logistica.mesas.repository.MesaRepository;
import com.chaquena.backend_logistica.pedidos.domain.CanalOrigenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoPagoEnum;
import com.chaquena.backend_logistica.pedidos.dto.CrearOrdenRequestDto;
import com.chaquena.backend_logistica.pedidos.dto.ItemOrdenRequestDto;
import com.chaquena.backend_logistica.pedidos.dto.OrdenResponseDto;
import com.chaquena.backend_logistica.pedidos.service.OrdenService;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.mensajeria.BotonBot;
import com.chaquena.backend_logistica.shared.mensajeria.EmisorBotInterno;
import com.chaquena.backend_logistica.shared.mensajeria.MensajeBot;
import com.chaquena.backend_logistica.shared.mensajeria.OpcionBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * El mozo levanta la comanda desde el bot interno.
 *
 * <p>Es la pieza que no existia cuando el canal era WhatsApp: alli el bot
 * interno solo servia para mover stock y el mozo tenia que ir al POS. Con el
 * mozo dentro del chat, la comanda entra por el mismo sitio por el que la
 * cocina la recibe, y la demostracion muestra el circuito entero —mozo, cocina,
 * cliente— sin abrir el navegador.
 *
 * <p>La comanda que produce no es distinta de la del POS: pasa por
 * {@link OrdenService#crear} igual que cualquier otra, con validacion de stock,
 * descuento por receta, promociones y evento de facturacion. Lo unico propio de
 * este camino es que el autor se resuelve por la cuenta vinculada en vez de por
 * el token de la sesion web.
 *
 * <p>El estado vive en Redis con caducidad corta: un mozo a medio armar una
 * comanda que se pierde por un reinicio la rehace en un minuto, y dejar
 * comandas a medias ocupando la base seria peor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MozoBotService {

    private static final String REDIS_PREFIX = "bot:sesion:mozo:";
    private static final Duration VIGENCIA_SESION = Duration.ofMinutes(30);

    /** Prefijo de todo lo que este flujo emite; el listener enruta por el. */
    public static final String PREFIJO = "pedido:";
    private static final String PREFIJO_TIPO = PREFIJO + "tipo:";
    private static final String PREFIJO_MESA = PREFIJO + "mesa:";
    private static final String PREFIJO_PLATO = PREFIJO + "plato:";
    private static final String PREFIJO_PAGO = PREFIJO + "pago:";
    private static final String SIN_NOTA = PREFIJO + "nota:ninguna";
    private static final String MAS_SI = PREFIJO + "mas:si";
    private static final String MAS_NO = PREFIJO + "mas:no";
    private static final String CONFIRMAR_SI = PREFIJO + "confirmar:si";
    private static final String CONFIRMAR_NO = PREFIJO + "confirmar:no";

    private static final String PASO_TIPO = "TIPO_ORDEN";
    private static final String PASO_MESA = "MESA";
    private static final String PASO_PLATO = "PLATO";
    private static final String PASO_NOTA = "NOTA";
    private static final String PASO_MAS = "MAS_PLATOS";
    private static final String PASO_PAGO = "PAGO";
    private static final String PASO_CONFIRMAR = "CONFIRMAR";

    private final MesaRepository mesaRepository;
    private final PlatilloService platilloService;
    private final OrdenService ordenService;
    private final IdentidadBotService identidad;
    private final EmisorBotInterno emisor;
    private final RedisTemplate<String, Object> redisTemplate;

    /** Si este mensaje le corresponde a este flujo. */
    public boolean atiende(String contenido) {
        return contenido != null && contenido.startsWith(PREFIJO);
    }

    /**
     * Si hay una comanda a medio armar con este remitente. Lo consulta el
     * listener antes de entregar un texto escrito a mano, que es la nota del
     * platillo y no lleva prefijo.
     */
    public boolean tieneSesionAbierta(String remitenteId) {
        return leerSesion(remitenteId) != null;
    }

    /** Abre la comanda. Lo llama el comando de barra {@code /pedido}. */
    public void iniciar(MensajeBot mensaje) {
        Trabajador mozo;
        try {
            mozo = identidad.exigirTrabajador(mensaje.remitenteId());
        } catch (ConflictoException e) {
            emisor.enviarTexto(mensaje.remitenteId(), e.getMessage());
            return;
        }

        log.info("🧾 [Bot IN] {} abre una comanda nueva.", mozo.getUsername());
        preguntarTipo(mensaje.remitenteId());
    }

    /** Continua una comanda abierta: una fila elegida, un boton o la nota tecleada. */
    public void procesar(MensajeBot mensaje) {
        String remitente = mensaje.remitenteId();

        Trabajador mozo;
        try {
            mozo = identidad.exigirTrabajador(remitente);
        } catch (ConflictoException e) {
            emisor.enviarTexto(remitente, e.getMessage());
            return;
        }

        String contenido = mensaje.contenido() == null ? "" : mensaje.contenido().trim();
        String normalizado = contenido.toLowerCase(Locale.ROOT);

        if (normalizado.equals("cancelar") || normalizado.equals("salir")) {
            borrarSesion(remitente);
            emisor.enviarTexto(remitente, "🚫 Comanda descartada. Escribe `/pedido` para empezar otra.");
            return;
        }

        Map<String, Object> sesion = leerSesion(remitente);
        if (sesion == null) {
            preguntarTipo(remitente);
            return;
        }

        CarritoBot carrito = CarritoBot.de(carritoDe(sesion));
        String paso = (String) sesion.get("pasoActual");

        switch (paso == null ? "" : paso) {
            case PASO_TIPO -> recibirTipo(remitente, contenido, sesion, carrito);
            case PASO_MESA -> recibirMesa(remitente, contenido, sesion, carrito);
            case PASO_PLATO -> recibirPlato(remitente, contenido, sesion, carrito);
            case PASO_NOTA -> recibirNota(remitente, contenido, sesion, carrito);
            case PASO_MAS -> recibirMasPlatos(remitente, contenido, sesion, carrito);
            case PASO_PAGO -> recibirPago(remitente, contenido, sesion, carrito);
            case PASO_CONFIRMAR -> recibirConfirmacion(remitente, contenido, sesion, carrito, mozo);
            default -> preguntarTipo(remitente);
        }
    }

    // =====================================================================
    // Pasos
    // =====================================================================

    private void preguntarTipo(String remitente) {
        guardarSesion(remitente, PASO_TIPO, CarritoBot.vacio());
        emisor.enviarBotones(remitente, "🧾 **Comanda nueva**\n¿De qué tipo es?",
                List.of(BotonBot.primario(PREFIJO_TIPO + "MESA", "Mesa"),
                        BotonBot.de(PREFIJO_TIPO + "RETIRO_LOCAL", "Para llevar"),
                        BotonBot.de(PREFIJO_TIPO + "DELIVERY", "Delivery")));
    }

    private void recibirTipo(String remitente, String contenido, Map<String, Object> sesion,
            CarritoBot carrito) {

        if (!contenido.startsWith(PREFIJO_TIPO)) {
            emisor.enviarTexto(remitente, "👆 Toca uno de los tres botones, por favor.");
            return;
        }

        TipoOrdenEnum tipo;
        try {
            tipo = TipoOrdenEnum.valueOf(contenido.substring(PREFIJO_TIPO.length()));
        } catch (IllegalArgumentException e) {
            preguntarTipo(remitente);
            return;
        }

        carrito.ponerTipoOrden(tipo);

        if (tipo == TipoOrdenEnum.MESA) {
            ofrecerMesas(remitente, carrito);
            return;
        }

        // El delivery armado por el mozo va sin direccion porque el cliente esta
        // al telefono con el: la direccion se completa desde el POS al despachar.
        // Se pide aqui para no dejar la comanda invalida.
        if (tipo == TipoOrdenEnum.DELIVERY) {
            emisor.enviarTexto(remitente,
                    "ℹ️ Para delivery, arma la comanda aquí y completa la dirección desde el POS "
                            + "antes de despachar.");
        }

        ofrecerPlatillos(remitente, carrito);
    }

    private void ofrecerMesas(String remitente, CarritoBot carrito) {
        List<Mesa> mesas = mesaRepository.findByActivaTrueOrderByZonaAscNumeroAsc().stream()
                .filter(m -> m.getEstado() != EstadoMesaEnum.INHABILITADA)
                .limit(emisor.maxOpciones())
                .toList();

        if (mesas.isEmpty()) {
            emisor.enviarTexto(remitente, "😕 No hay mesas activas dadas de alta.");
            borrarSesion(remitente);
            return;
        }

        List<OpcionBot> opciones = mesas.stream()
                .map(m -> new OpcionBot(PREFIJO_MESA + m.getId(),
                        "Mesa " + m.getNumero(),
                        etiquetaMesa(m)))
                .toList();

        guardarSesion(remitente, PASO_MESA, carrito);
        emisor.enviarOpciones(remitente, "🪑 Mesas", "¿En qué mesa?", "Ver mesas", opciones);
    }

    private void recibirMesa(String remitente, String contenido, Map<String, Object> sesion,
            CarritoBot carrito) {

        if (!contenido.startsWith(PREFIJO_MESA)) {
            emisor.enviarTexto(remitente, "👆 Elige una mesa de la lista, por favor.");
            return;
        }

        carrito.ponerMesa(UUID.fromString(contenido.substring(PREFIJO_MESA.length())));
        ofrecerPlatillos(remitente, carrito);
    }

    private void ofrecerPlatillos(String remitente, CarritoBot carrito) {
        List<PlatilloDisponibleDto> disponibles = platilloService.menuDisponible().stream()
                .filter(PlatilloDisponibleDto::isDisponible)
                .limit(emisor.maxOpciones())
                .toList();

        if (disponibles.isEmpty()) {
            emisor.enviarTexto(remitente, "😔 Cocina no tiene ningún platillo disponible ahora mismo.");
            borrarSesion(remitente);
            return;
        }

        List<OpcionBot> opciones = disponibles.stream()
                .map(p -> new OpcionBot(PREFIJO_PLATO + p.getId(), p.getNombre(),
                        soles(p.getPrecioVentaBase())))
                .toList();

        guardarSesion(remitente, PASO_PLATO, carrito);
        emisor.enviarOpciones(remitente, "🍽️ Platillos",
                carrito.vacioDeItems() ? "¿Qué pidió?" : "¿Qué más pidió?", "Ver carta", opciones);
    }

    private void recibirPlato(String remitente, String contenido, Map<String, Object> sesion,
            CarritoBot carrito) {

        if (!contenido.startsWith(PREFIJO_PLATO)) {
            emisor.enviarTexto(remitente, "👆 Elige un platillo de la lista, por favor.");
            return;
        }

        UUID platilloId = UUID.fromString(contenido.substring(PREFIJO_PLATO.length()));
        Optional<PlatilloDisponibleDto> platillo = platilloService.menuDisponible().stream()
                .filter(p -> p.getId().equals(platilloId))
                .findFirst();

        if (platillo.isEmpty() || !platillo.get().isDisponible()) {
            emisor.enviarTexto(remitente, "😔 Ese platillo se acaba de agotar. Elige otro.");
            ofrecerPlatillos(remitente, carrito);
            return;
        }

        carrito.agregarPlatillo(platilloId, platillo.get().getNombre(), platillo.get().getPrecioVentaBase());
        guardarSesion(remitente, PASO_NOTA, carrito);

        emisor.enviarBotones(remitente,
                "✏️ ¿Alguna indicación para el **" + carrito.nombreEnCurso() + "**?\n"
                        + "Escríbela o toca el botón.",
                List.of(BotonBot.de(SIN_NOTA, "Sin indicaciones")));
    }

    private void recibirNota(String remitente, String contenido, Map<String, Object> sesion,
            CarritoBot carrito) {

        if (!SIN_NOTA.equals(contenido)) {
            carrito.ponerNotaAlEnCurso(contenido);
        }

        guardarSesion(remitente, PASO_MAS, carrito);
        emisor.enviarBotones(remitente,
                "✅ Van " + carrito.cantidadDeItems() + " platillo(s) por " + soles(carrito.totalEstimado())
                        + ".\n¿Algo más?",
                List.of(BotonBot.de(MAS_SI, "Agregar otro"),
                        BotonBot.primario(MAS_NO, "Cerrar comanda")));
    }

    private void recibirMasPlatos(String remitente, String contenido, Map<String, Object> sesion,
            CarritoBot carrito) {

        if (MAS_SI.equals(contenido)) {
            ofrecerPlatillos(remitente, carrito);
            return;
        }
        if (!MAS_NO.equals(contenido)) {
            emisor.enviarTexto(remitente, "👆 Toca uno de los dos botones, por favor.");
            return;
        }

        guardarSesion(remitente, PASO_PAGO, carrito);
        emisor.enviarBotones(remitente, "💳 ¿Cómo va a pagar?",
                List.of(BotonBot.de(PREFIJO_PAGO + "EFECTIVO", "Efectivo"),
                        BotonBot.de(PREFIJO_PAGO + "E_WALLET", "Yape / Plin"),
                        BotonBot.de(PREFIJO_PAGO + "TARJETA", "Tarjeta")));
    }

    private void recibirPago(String remitente, String contenido, Map<String, Object> sesion,
            CarritoBot carrito) {

        if (!contenido.startsWith(PREFIJO_PAGO)) {
            emisor.enviarTexto(remitente, "👆 Elige el método de pago con los botones.");
            return;
        }

        TipoPagoEnum pago;
        try {
            pago = TipoPagoEnum.valueOf(contenido.substring(PREFIJO_PAGO.length()));
        } catch (IllegalArgumentException e) {
            emisor.enviarTexto(remitente, "👆 Elige el método de pago con los botones.");
            return;
        }

        carrito.ponerTipoPago(pago);
        guardarSesion(remitente, PASO_CONFIRMAR, carrito);

        StringBuilder resumen = new StringBuilder("🧾 **Comanda por confirmar**\n");
        resumen.append("\n**Tipo:** ").append(etiquetaTipo(carrito.tipoOrden()));
        if (carrito.mesa() != null) {
            mesaRepository.findById(carrito.mesa())
                    .ifPresent(m -> resumen.append("  ·  Mesa ").append(m.getNumero()));
        }
        for (CarritoBot.ItemCarrito item : carrito.lineas()) {
            resumen.append("\n• ").append(item.cantidad()).append("x ").append(item.nombre())
                    .append("  ").append(soles(item.precio()));
            if (item.nota() != null) {
                resumen.append("\n   📝 _").append(item.nota()).append("_");
            }
        }
        resumen.append("\n\n**Total estimado:** ").append(soles(carrito.totalEstimado()));
        resumen.append("\n**Pago:** ").append(etiquetaPago(carrito.tipoPago()));

        emisor.enviarTexto(remitente, resumen.toString());
        emisor.enviarBotones(remitente, "¿Mando la comanda a cocina?",
                List.of(BotonBot.exito(CONFIRMAR_SI, "Confirmar"),
                        BotonBot.peligro(CONFIRMAR_NO, "Descartar")));
    }

    private void recibirConfirmacion(String remitente, String contenido, Map<String, Object> sesion,
            CarritoBot carrito, Trabajador mozo) {

        if (CONFIRMAR_NO.equals(contenido)) {
            borrarSesion(remitente);
            emisor.enviarTexto(remitente, "🚫 Comanda descartada.");
            return;
        }
        if (!CONFIRMAR_SI.equals(contenido)) {
            emisor.enviarTexto(remitente, "👆 Toca *Confirmar* o *Descartar*.");
            return;
        }

        crearComanda(remitente, carrito, mozo);
    }

    // =====================================================================
    // Cierre
    // =====================================================================

    private void crearComanda(String remitente, CarritoBot carrito, Trabajador mozo) {
        if (carrito.vacioDeItems()) {
            emisor.enviarTexto(remitente, "🛒 La comanda quedó vacía. Escribe `/pedido` para empezar otra.");
            borrarSesion(remitente);
            return;
        }

        List<ItemOrdenRequestDto> items = new ArrayList<>();
        for (CarritoBot.ItemCarrito item : carrito.lineas()) {
            items.add(ItemOrdenRequestDto.builder()
                    .platilloId(item.platilloId())
                    .cantidad(item.cantidad())
                    .excepcionesNota(item.nota())
                    .complementos(List.of())
                    .build());
        }

        CrearOrdenRequestDto peticion = CrearOrdenRequestDto.builder()
                .tipoOrden(carrito.tipoOrden())
                .canalOrigen(CanalOrigenEnum.DISCORD_BOT)
                .mesaId(carrito.mesa())
                .tipoPago(carrito.tipoPago())
                .items(items)
                .build();

        OrdenResponseDto orden;
        try {
            // El mozo si tiene identidad: la comanda se firma con su id y no con
            // la del bot, para que el kardex y las comisiones digan quien la tomo.
            orden = ordenService.crearComoTrabajador(peticion, mozo.getId(), mozo.getUsername());
        } catch (Exception e) {
            log.warn("No se pudo crear la comanda del mozo {}: {}", mozo.getUsername(), e.getMessage());
            emisor.enviarTexto(remitente, "😔 No se pudo registrar la comanda: " + e.getMessage());
            return;
        }

        borrarSesion(remitente);
        emisor.enviarTexto(remitente, "✅ **Comanda " + cortoDe(orden.getId()) + " enviada a cocina.**\n"
                + "Total: " + soles(orden.getMontoTotal()));
        log.info("✅ Comanda {} creada por el mozo {} desde el bot.", orden.getId(), mozo.getUsername());
    }

    // =====================================================================
    // Sesion en Redis
    // =====================================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> leerSesion(String remitente) {
        try {
            return (Map<String, Object>) redisTemplate.opsForValue().get(REDIS_PREFIX + remitente);
        } catch (Exception e) {
            log.error("⚠️ Redis no respondio al leer la sesion del mozo: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> carritoDe(Map<String, Object> sesion) {
        Object carrito = sesion.get("carrito");
        return carrito instanceof Map ? (Map<String, Object>) carrito : null;
    }

    private void guardarSesion(String remitente, String paso, CarritoBot carrito) {
        Map<String, Object> sesion = new HashMap<>();
        sesion.put("pasoActual", paso);
        sesion.put("carrito", carrito.instantanea());
        try {
            redisTemplate.opsForValue().set(REDIS_PREFIX + remitente, sesion, VIGENCIA_SESION);
        } catch (Exception e) {
            log.error("❌ No se pudo guardar la sesion del mozo en Redis: {}", e.getMessage());
        }
    }

    private void borrarSesion(String remitente) {
        try {
            redisTemplate.delete(REDIS_PREFIX + remitente);
        } catch (Exception e) {
            log.error("❌ No se pudo borrar la sesion del mozo en Redis: {}", e.getMessage());
        }
    }

    // =====================================================================
    // Formato
    // =====================================================================

    private static String etiquetaMesa(Mesa mesa) {
        String zona = mesa.getZona() == null ? "" : mesa.getZona() + " · ";
        return zona + (mesa.getEstado() == EstadoMesaEnum.OCUPADA ? "Ocupada" : "Libre");
    }

    private static String etiquetaTipo(TipoOrdenEnum tipo) {
        return switch (tipo) {
            case MESA -> "Mesa";
            case RETIRO_LOCAL -> "Para llevar";
            case DELIVERY -> "Delivery";
        };
    }

    private static String etiquetaPago(TipoPagoEnum tipoPago) {
        return switch (tipoPago) {
            case EFECTIVO -> "Efectivo";
            case E_WALLET -> "Yape / Plin";
            case TARJETA -> "Tarjeta";
        };
    }

    private static String soles(BigDecimal monto) {
        return monto == null ? "S/ 0.00" : "S/ " + monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String cortoDe(UUID id) {
        return id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
