package com.chaquena.backend_logistica.delivery.bot.service;

import com.chaquena.backend_logistica.clientes.domain.Cliente;
import com.chaquena.backend_logistica.clientes.dto.ClienteAnonimoRequestDto;
import com.chaquena.backend_logistica.clientes.repository.ClienteRepository;
import com.chaquena.backend_logistica.clientes.service.ClienteService;
import com.chaquena.backend_logistica.delivery.bot.domain.PasoConversacionEnum;
import com.chaquena.backend_logistica.delivery.domain.SesionBot;
import com.chaquena.backend_logistica.delivery.repository.SesionBotRepository;
import com.chaquena.backend_logistica.inventario.domain.ComplementoPlatillo;
import com.chaquena.backend_logistica.inventario.dto.PlatilloDisponibleDto;
import com.chaquena.backend_logistica.inventario.repository.ComplementoPlatilloRepository;
import com.chaquena.backend_logistica.inventario.service.PlatilloService;
import com.chaquena.backend_logistica.pedidos.bot.service.CarritoBot;
import com.chaquena.backend_logistica.pedidos.domain.CanalOrigenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoPagoEnum;
import com.chaquena.backend_logistica.pedidos.dto.ComplementoItemDto;
import com.chaquena.backend_logistica.pedidos.dto.CrearOrdenRequestDto;
import com.chaquena.backend_logistica.pedidos.dto.ItemOrdenRequestDto;
import com.chaquena.backend_logistica.pedidos.dto.OrdenResponseDto;
import com.chaquena.backend_logistica.pedidos.service.OrdenService;
import com.chaquena.backend_logistica.shared.mensajeria.BotonBot;
import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import com.chaquena.backend_logistica.shared.mensajeria.EmisorBotCliente;
import com.chaquena.backend_logistica.shared.mensajeria.MensajeBot;
import com.chaquena.backend_logistica.shared.mensajeria.OpcionBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bot OUT: la conversacion de pedidos con el cliente final.
 *
 * <p>El cliente nunca escribe libremente salvo en dos sitios (la indicacion del
 * platillo y la direccion de entrega). Todo lo demas son listas y botones, que
 * devuelven identificadores exactos y evitan tener que interpretar frases como
 * "kiero 1 ceviche p porfa".
 *
 * <p>El estado vive en la tabla {@code sesiones_bot} y no en Redis, al
 * contrario que el bot de stock. Un carrito a medio llenar es trabajo del
 * cliente: si el backend se reinicia, perderlo significa hacerle repetir el
 * pedido entero. La tabla trae su propia caducidad en {@code expira_en}.
 *
 * <p>Este servicio no lleva {@code @Transactional} a proposito. Cuando la
 * creacion de la comanda falla por stock o por bloqueo de fraude, su
 * transaccion se deshace sola y aqui hace falta seguir vivo para avisarle al
 * cliente y dejarle la sesion en un paso coherente; con una transaccion comun,
 * ese guardado posterior moriria con un rollback-only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClienteBotService {

    /** Una conversacion abandonada deja de ocupar la sesion pasadas dos horas. */
    private static final int HORAS_VIGENCIA_SESION = 2;

    private static final String PREFIJO_CATEGORIA = "cliente:cat:";
    private static final String PREFIJO_PLATO = "cliente:plato:";
    private static final String PREFIJO_COMPLEMENTO = "cliente:comp:";
    private static final String SIN_COMPLEMENTO = "cliente:comp:ninguno";
    private static final String SIN_NOTA = "cliente:nota:ninguna";
    private static final String MAS_PLATOS_SI = "cliente:mas:si";
    private static final String MAS_PLATOS_NO = "cliente:mas:no";
    private static final String ENTREGA_DELIVERY = "cliente:entrega:delivery";
    private static final String ENTREGA_RECOJO = "cliente:entrega:recojo";
    private static final String PAGO_EFECTIVO = "cliente:pago:efectivo";
    private static final String PAGO_EWALLET = "cliente:pago:ewallet";
    private static final String PAGO_TARJETA = "cliente:pago:tarjeta";
    private static final String CONFIRMAR_SI = "cliente:confirmar:si";
    private static final String CONFIRMAR_NO = "cliente:confirmar:no";

    private final SesionBotRepository sesionRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteService clienteService;
    private final PlatilloService platilloService;
    private final ComplementoPlatilloRepository complementoRepository;
    private final OrdenService ordenService;
    private final EmisorBotCliente emisor;

    // =====================================================================
    // Entrada
    // =====================================================================

    public void procesar(MensajeBot mensaje) {
        if (mensaje == null || mensaje.contenido() == null) {
            return;
        }

        emisor.acusarRecibo(mensaje.messageId());
        log.info("💬 [Bot OUT] {} -> '{}'", mensaje.remitenteId(), mensaje.contenido());

        try {
            despachar(mensaje);
        } catch (Exception e) {
            log.error("❌ [Bot OUT] Error atendiendo a {}: {}", mensaje.remitenteId(), e.getMessage(), e);
            emisor.enviarTexto(mensaje.remitenteId(),
                    "😕 Tuvimos un problema atendiendo tu pedido. Escribe *menu* para empezar de nuevo.");
        }
    }

    private void despachar(MensajeBot mensaje) {
        String remitente = mensaje.remitenteId();
        String contenido = mensaje.contenido();

        // Salidas de emergencia disponibles en cualquier paso: sin ellas, una
        // sesion atascada solo se destraba esperando a que caduque.
        String normalizado = contenido.trim().toLowerCase(Locale.ROOT);
        if (normalizado.equals("cancelar") || normalizado.equals("salir")) {
            sesionRepository.findByCanalAndRemitenteId(CanalBot.OUT, remitente)
                    .ifPresent(sesionRepository::delete);
            emisor.enviarTexto(remitente, "🚫 Pedido cancelado. Escribe *hola* cuando quieras volver a empezar.");
            return;
        }

        SesionBot sesion = sesionVigente(remitente, mensaje.remitenteNombre());

        if (normalizado.equals("hola") || normalizado.equals("menu") || normalizado.equals("menú")
                || sesion.getPasoActual().equals(PasoConversacionEnum.INICIO.name())) {
            iniciarConversacion(sesion, mensaje.remitenteNombre());
            return;
        }

        PasoConversacionEnum paso = PasoConversacionEnum.valueOf(sesion.getPasoActual());
        CarritoBot carrito = CarritoBot.de(sesion.getCarritoJson());

        switch (paso) {
            case ELIGIENDO_CATEGORIA -> recibirCategoria(sesion, carrito, contenido);
            case ELIGIENDO_PLATO -> recibirPlatillo(sesion, carrito, contenido);
            case ELIGIENDO_COMPLEMENTO -> recibirComplemento(sesion, carrito, contenido);
            case ESCRIBIENDO_NOTA -> recibirNota(sesion, carrito, contenido);
            case DECIDIENDO_MAS_PLATOS -> recibirDecisionMasPlatos(sesion, carrito, contenido);
            case ELIGIENDO_ENTREGA -> recibirTipoEntrega(sesion, carrito, contenido);
            case ESCRIBIENDO_DIRECCION -> recibirDireccion(sesion, carrito, contenido);
            case ELIGIENDO_PAGO -> recibirTipoPago(sesion, carrito, contenido);
            case CONFIRMANDO -> recibirConfirmacion(sesion, carrito, contenido);
            default -> iniciarConversacion(sesion, mensaje.remitenteNombre());
        }
    }

    // =====================================================================
    // Pasos de la conversacion
    // =====================================================================

    private void iniciarConversacion(SesionBot sesion, String nombreRemitente) {
        String remitente = sesion.getRemitenteId();
        CarritoBot carrito = CarritoBot.vacio();

        String saludo = buscarCliente(remitente)
                .map(cliente -> "👋 ¡Hola de nuevo, " + cliente.getNombres() + "!")
                .orElseGet(() -> nombreRemitente != null && !nombreRemitente.isBlank()
                        ? "👋 ¡Bienvenido a **Chaquena**, " + nombreRemitente + "!"
                        : "👋 ¡Bienvenido a **Chaquena**!");
        emisor.enviarTexto(remitente,
                saludo + "\nArmemos tu pedido. Escribe *cancelar* en cualquier momento para salir.");

        ofrecerCarta(sesion, carrito, null);
    }

    /**
     * Muestra la carta. Si hay mas platillos disponibles de los que caben en una
     * lista, primero se pregunta la categoria para que ninguno quede fuera de
     * pantalla sin que el cliente lo sepa. Cuantos caben lo decide el proveedor:
     * con Discord son 25 y con WhatsApp eran 10, asi que este rodeo se dispara
     * mucho menos que antes.
     */
    private void ofrecerCarta(SesionBot sesion, CarritoBot carrito, Integer categoriaId) {
        String remitente = sesion.getRemitenteId();
        int tope = emisor.maxOpciones();

        List<PlatilloDisponibleDto> disponibles = platilloService.menuDisponible().stream()
                .filter(PlatilloDisponibleDto::isDisponible)
                .toList();

        if (disponibles.isEmpty()) {
            emisor.enviarTexto(remitente,
                    "😔 Ahora mismo no tenemos platillos disponibles. Vuelve a escribirnos en un rato.");
            guardar(sesion, PasoConversacionEnum.INICIO, carrito);
            return;
        }

        List<PlatilloDisponibleDto> deLaCategoria = categoriaId == null
                ? disponibles
                : disponibles.stream().filter(p -> categoriaId.equals(p.getCategoriaId())).toList();

        boolean hayQueAgrupar = categoriaId == null
                && disponibles.size() > tope
                && contarCategorias(disponibles) > 1;

        if (hayQueAgrupar) {
            List<OpcionBot> opciones = new ArrayList<>();
            Map<Integer, String> porCategoria = new LinkedHashMap<>();
            disponibles.forEach(p -> porCategoria.putIfAbsent(p.getCategoriaId(), p.getCategoriaNombre()));
            porCategoria.forEach((id, nombre) -> opciones.add(OpcionBot.de(PREFIJO_CATEGORIA + id, nombre)));

            emisor.enviarOpciones(remitente, "🍽️ Nuestra carta",
                    "¿Qué tipo de plato te provoca?", "Ver categorías", opciones);
            guardar(sesion, PasoConversacionEnum.ELIGIENDO_CATEGORIA, carrito);
            return;
        }

        List<OpcionBot> opciones = deLaCategoria.stream()
                .limit(tope)
                .map(p -> new OpcionBot(PREFIJO_PLATO + p.getId(), p.getNombre(),
                        soles(p.getPrecioVentaBase())))
                .toList();

        emisor.enviarOpciones(remitente, "🍽️ Elige tu platillo",
                carrito.vacioDeItems() ? "Estos son los platos disponibles:" : "¿Qué más le agregamos al pedido?",
                "Ver platillos", opciones);
        guardar(sesion, PasoConversacionEnum.ELIGIENDO_PLATO, carrito);
    }

    private void recibirCategoria(SesionBot sesion, CarritoBot carrito, String contenido) {
        if (!contenido.startsWith(PREFIJO_CATEGORIA)) {
            emisor.enviarTexto(sesion.getRemitenteId(), "👆 Elige una categoría de la lista, por favor.");
            return;
        }

        try {
            ofrecerCarta(sesion, carrito, Integer.valueOf(contenido.substring(PREFIJO_CATEGORIA.length())));
        } catch (NumberFormatException e) {
            ofrecerCarta(sesion, carrito, null);
        }
    }

    private void recibirPlatillo(SesionBot sesion, CarritoBot carrito, String contenido) {
        String remitente = sesion.getRemitenteId();

        if (!contenido.startsWith(PREFIJO_PLATO)) {
            emisor.enviarTexto(remitente, "👆 Elige un platillo de la lista, por favor.");
            return;
        }

        UUID platilloId = UUID.fromString(contenido.substring(PREFIJO_PLATO.length()));
        Optional<PlatilloDisponibleDto> platillo = platilloService.menuDisponible().stream()
                .filter(p -> p.getId().equals(platilloId))
                .findFirst();

        if (platillo.isEmpty() || !platillo.get().isDisponible()) {
            emisor.enviarTexto(remitente, "😔 Ese platillo se acaba de agotar. Elige otro de la carta.");
            ofrecerCarta(sesion, carrito, null);
            return;
        }

        carrito.agregarPlatillo(platilloId, platillo.get().getNombre(), platillo.get().getPrecioVentaBase());
        ofrecerComplementos(sesion, carrito);
    }

    private void ofrecerComplementos(SesionBot sesion, CarritoBot carrito) {
        String remitente = sesion.getRemitenteId();
        List<ComplementoPlatillo> complementos = complementoRepository.findByActivoTrue();

        if (complementos.isEmpty()) {
            pedirNota(sesion, carrito);
            return;
        }

        List<OpcionBot> opciones = new ArrayList<>();
        opciones.add(OpcionBot.de(SIN_COMPLEMENTO, "Sin complemento"));
        complementos.stream()
                .limit(emisor.maxOpciones() - 1L)
                .forEach(c -> opciones.add(new OpcionBot(
                        PREFIJO_COMPLEMENTO + c.getId(), c.getNombre(), soles(c.getPrecioAdicional()))));

        emisor.enviarOpciones(remitente, "🥤 Complementos",
                "¿Le agregas algo a tu **" + carrito.nombreEnCurso() + "**?", "Ver complementos", opciones);
        guardar(sesion, PasoConversacionEnum.ELIGIENDO_COMPLEMENTO, carrito);
    }

    private void recibirComplemento(SesionBot sesion, CarritoBot carrito, String contenido) {
        if (SIN_COMPLEMENTO.equals(contenido)) {
            pedirNota(sesion, carrito);
            return;
        }

        if (!contenido.startsWith(PREFIJO_COMPLEMENTO)) {
            emisor.enviarTexto(sesion.getRemitenteId(),
                    "👆 Elige un complemento de la lista o la opción *Sin complemento*.");
            return;
        }

        UUID complementoId = UUID.fromString(contenido.substring(PREFIJO_COMPLEMENTO.length()));
        Optional<ComplementoPlatillo> complemento = complementoRepository.findById(complementoId);

        if (complemento.isEmpty()) {
            emisor.enviarTexto(sesion.getRemitenteId(), "😔 Ese complemento ya no está disponible.");
            ofrecerComplementos(sesion, carrito);
            return;
        }

        carrito.agregarComplementoAlEnCurso(complementoId, complemento.get().getNombre(),
                complemento.get().getPrecioAdicional());
        pedirNota(sesion, carrito);
    }

    private void pedirNota(SesionBot sesion, CarritoBot carrito) {
        emisor.enviarBotones(sesion.getRemitenteId(),
                "✏️ ¿Alguna indicación para tu **" + carrito.nombreEnCurso() + "**?\n"
                        + "Escríbela (ej. _sin cebolla_, _bien cocido_) o toca el botón.",
                List.of(BotonBot.de(SIN_NOTA, "Sin indicaciones")));
        guardar(sesion, PasoConversacionEnum.ESCRIBIENDO_NOTA, carrito);
    }

    private void recibirNota(SesionBot sesion, CarritoBot carrito, String contenido) {
        if (!SIN_NOTA.equals(contenido)) {
            carrito.ponerNotaAlEnCurso(contenido);
        }
        preguntarPorMasPlatos(sesion, carrito);
    }

    private void preguntarPorMasPlatos(SesionBot sesion, CarritoBot carrito) {
        emisor.enviarBotones(sesion.getRemitenteId(),
                "✅ Listo. Llevas " + carrito.cantidadDeItems() + " platillo(s) por "
                        + soles(carrito.totalEstimado()) + ".\n¿Agregamos algo más?",
                List.of(BotonBot.de(MAS_PLATOS_SI, "Agregar otro"),
                        BotonBot.primario(MAS_PLATOS_NO, "Continuar")));
        guardar(sesion, PasoConversacionEnum.DECIDIENDO_MAS_PLATOS, carrito);
    }

    private void recibirDecisionMasPlatos(SesionBot sesion, CarritoBot carrito, String contenido) {
        if (MAS_PLATOS_SI.equals(contenido)) {
            ofrecerCarta(sesion, carrito, null);
            return;
        }
        if (MAS_PLATOS_NO.equals(contenido)) {
            preguntarTipoEntrega(sesion, carrito);
            return;
        }
        emisor.enviarTexto(sesion.getRemitenteId(), "👆 Toca uno de los dos botones, por favor.");
    }

    private void preguntarTipoEntrega(SesionBot sesion, CarritoBot carrito) {
        emisor.enviarBotones(sesion.getRemitenteId(),
                "🛵 ¿Cómo prefieres recibirlo?",
                List.of(BotonBot.primario(ENTREGA_DELIVERY, "Delivery"),
                        BotonBot.de(ENTREGA_RECOJO, "Recojo en local")));
        guardar(sesion, PasoConversacionEnum.ELIGIENDO_ENTREGA, carrito);
    }

    private void recibirTipoEntrega(SesionBot sesion, CarritoBot carrito, String contenido) {
        String remitente = sesion.getRemitenteId();

        if (ENTREGA_RECOJO.equals(contenido)) {
            carrito.ponerTipoOrden(TipoOrdenEnum.RETIRO_LOCAL);
            preguntarTipoPago(sesion, carrito);
            return;
        }

        if (!ENTREGA_DELIVERY.equals(contenido)) {
            emisor.enviarTexto(remitente, "👆 Toca *Delivery* o *Recojo en local*.");
            return;
        }

        carrito.ponerTipoOrden(TipoOrdenEnum.DELIVERY);

        String habitual = buscarCliente(remitente).map(Cliente::getDireccionHabitual).orElse(null);
        String pregunta = habitual != null && !habitual.isBlank()
                ? "📍 Escríbenos la dirección de entrega.\nLa última que usaste fue: _" + habitual + "_"
                : "📍 Escríbenos la dirección de entrega, con alguna referencia si puedes.";

        emisor.enviarTexto(remitente, pregunta);
        guardar(sesion, PasoConversacionEnum.ESCRIBIENDO_DIRECCION, carrito);
    }

    private void recibirDireccion(SesionBot sesion, CarritoBot carrito, String contenido) {
        if (contenido.isBlank() || contenido.trim().length() < 5) {
            emisor.enviarTexto(sesion.getRemitenteId(),
                    "🤔 Esa dirección es muy corta. Escríbela completa, por favor.");
            return;
        }
        carrito.ponerDireccion(contenido.trim());
        preguntarTipoPago(sesion, carrito);
    }

    private void preguntarTipoPago(SesionBot sesion, CarritoBot carrito) {
        emisor.enviarBotones(sesion.getRemitenteId(),
                "💳 ¿Cómo vas a pagar?",
                List.of(BotonBot.de(PAGO_EFECTIVO, "Efectivo"),
                        BotonBot.de(PAGO_EWALLET, "Yape / Plin"),
                        BotonBot.de(PAGO_TARJETA, "Tarjeta")));
        guardar(sesion, PasoConversacionEnum.ELIGIENDO_PAGO, carrito);
    }

    private void recibirTipoPago(SesionBot sesion, CarritoBot carrito, String contenido) {
        TipoPagoEnum tipoPago = switch (contenido) {
            case PAGO_EFECTIVO -> TipoPagoEnum.EFECTIVO;
            case PAGO_EWALLET -> TipoPagoEnum.E_WALLET;
            case PAGO_TARJETA -> TipoPagoEnum.TARJETA;
            default -> null;
        };

        if (tipoPago == null) {
            emisor.enviarTexto(sesion.getRemitenteId(), "👆 Elige un método de pago con los botones.");
            return;
        }

        carrito.ponerTipoPago(tipoPago);
        enviarResumen(sesion, carrito);
    }

    private void enviarResumen(SesionBot sesion, CarritoBot carrito) {
        StringBuilder resumen = new StringBuilder("🧾 **Resumen de tu pedido**\n");

        for (CarritoBot.ItemCarrito item : carrito.lineas()) {
            resumen.append("\n• ").append(item.cantidad()).append("x ").append(item.nombre())
                    .append("  ").append(soles(item.precio()));
            item.complementos().forEach(c -> resumen.append("\n   ➕ ").append(c.nombre())
                    .append("  ").append(soles(c.precio())));
            if (item.nota() != null) {
                resumen.append("\n   📝 _").append(item.nota()).append("_");
            }
        }

        resumen.append("\n\n**Total estimado:** ").append(soles(carrito.totalEstimado()));
        resumen.append("\n**Entrega:** ").append(carrito.tipoOrden() == TipoOrdenEnum.DELIVERY
                ? "Delivery a " + carrito.direccion()
                : "Recojo en local");
        resumen.append("\n**Pago:** ").append(etiquetaPago(carrito.tipoPago()));

        emisor.enviarTexto(sesion.getRemitenteId(), resumen.toString());
        emisor.enviarBotones(sesion.getRemitenteId(), "¿Confirmamos el pedido?",
                List.of(BotonBot.exito(CONFIRMAR_SI, "Confirmar"),
                        BotonBot.peligro(CONFIRMAR_NO, "Cancelar")));
        guardar(sesion, PasoConversacionEnum.CONFIRMANDO, carrito);
    }

    private void recibirConfirmacion(SesionBot sesion, CarritoBot carrito, String contenido) {
        String remitente = sesion.getRemitenteId();

        if (CONFIRMAR_NO.equals(contenido)) {
            sesionRepository.delete(sesion);
            emisor.enviarTexto(remitente, "🚫 Pedido cancelado. Escribe *hola* cuando quieras pedir de nuevo.");
            return;
        }

        if (!CONFIRMAR_SI.equals(contenido)) {
            emisor.enviarTexto(remitente, "👆 Toca *Confirmar* o *Cancelar*.");
            return;
        }

        crearComanda(sesion, carrito);
    }

    // =====================================================================
    // Cierre: la comanda oficial
    // =====================================================================

    private void crearComanda(SesionBot sesion, CarritoBot carrito) {
        String remitente = sesion.getRemitenteId();

        if (carrito.vacioDeItems()) {
            emisor.enviarTexto(remitente, "🛒 Tu pedido quedó vacío. Escribe *menu* para empezar de nuevo.");
            reiniciar(sesion);
            return;
        }

        UUID clienteId = resolverClienteId(remitente, carrito.direccion());

        List<ItemOrdenRequestDto> items = carrito.lineas().stream()
                .map(item -> ItemOrdenRequestDto.builder()
                        .platilloId(item.platilloId())
                        .cantidad(item.cantidad())
                        .excepcionesNota(item.nota())
                        .complementos(item.complementos().stream()
                                .map(c -> ComplementoItemDto.builder()
                                        .complementoId(c.id())
                                        .cantidad(1)
                                        .build())
                                .toList())
                        .build())
                .toList();

        CrearOrdenRequestDto peticion = CrearOrdenRequestDto.builder()
                .clienteId(clienteId)
                .tipoOrden(carrito.tipoOrden())
                .canalOrigen(CanalOrigenEnum.DISCORD_BOT)
                .direccionDelivery(carrito.direccion())
                .tipoPago(carrito.tipoPago())
                .items(items)
                .build();

        OrdenResponseDto orden;
        try {
            orden = ordenService.crear(peticion);
        } catch (Exception e) {
            // El motivo real (stock insuficiente, cliente bloqueado por fraude,
            // direccion faltante) ya viene redactado desde el modulo de pedidos.
            log.warn("No se pudo crear la comanda del bot de clientes para {}: {}", remitente, e.getMessage());
            emisor.enviarTexto(remitente, "😔 No pudimos registrar tu pedido: " + e.getMessage()
                    + "\n\nEscribe *menu* para armarlo de nuevo.");
            reiniciar(sesion);
            return;
        }

        sesionRepository.delete(sesion);

        StringBuilder confirmacion = new StringBuilder("🎉 **¡Pedido confirmado!**\n\n");
        confirmacion.append("**Código:** ").append(cortoDe(orden.getId()));
        confirmacion.append("\n**Total:** ").append(soles(orden.getMontoTotal()));
        if (orden.getMontoDescuento() != null && orden.getMontoDescuento().signum() > 0) {
            confirmacion.append("  (descuento ").append(soles(orden.getMontoDescuento())).append(")");
        }

        if (orden.getCodigoOtpEntrega() != null) {
            confirmacion.append("\n\n🔐 **Tu código de entrega es ").append(orden.getCodigoOtpEntrega()).append("**.");
            confirmacion.append("\nDáselo al repartidor solo cuando tengas el pedido en la mano.");
        } else {
            confirmacion.append("\n\n🏠 Te avisamos por aquí cuando esté listo para recoger.");
        }
        confirmacion.append("\n\n⏱️ En cuanto cocina lo tome te decimos cuánto va a tardar.");

        emisor.enviarTexto(remitente, confirmacion.toString());
        log.info("✅ Comanda {} creada desde el Bot OUT para {}", orden.getId(), remitente);
    }

    /**
     * Una cuenta que pide por primera vez todavia no es un cliente en la base.
     * Se le abre una ficha anonima con su identificador para que la comanda
     * tenga a quien colgarse y para que el historial y la fidelizacion lo
     * reconozcan la proxima vez.
     */
    private UUID resolverClienteId(String remitente, String direccion) {
        return buscarCliente(remitente)
                .map(Cliente::getId)
                .orElseGet(() -> clienteService.crearAnonimo(ClienteAnonimoRequestDto.builder()
                        .nombreReferencia("Cliente Discord")
                        .discordUserId(remitente)
                        .direccionHabitual(direccion)
                        .build()).getId());
    }

    /**
     * Cliente detras del remitente. Se prueba primero la cuenta de Discord y
     * luego el celular, porque las fichas creadas cuando el canal era WhatsApp
     * siguen teniendo su numero y nada mas: un cliente antiguo que vuelva por
     * Discord se reconoce solo si alguien le anota la cuenta, pero al menos no
     * se pierde el historial de los que ya estaban por telefono.
     */
    private Optional<Cliente> buscarCliente(String remitente) {
        Optional<Cliente> porDiscord = clienteRepository.findByDiscordUserId(remitente);
        if (porDiscord.isPresent()) {
            return porDiscord;
        }
        Optional<Cliente> exacto = clienteRepository.findByCelular(remitente);
        if (exacto.isPresent()) {
            return exacto;
        }
        if (remitente.startsWith("51") && remitente.length() > 9 && remitente.length() <= 15) {
            return clienteRepository.findByCelular(remitente.substring(2));
        }
        return Optional.empty();
    }

    // =====================================================================
    // Sesion
    // =====================================================================

    private SesionBot sesionVigente(String remitente, String nombre) {
        Optional<SesionBot> existente = sesionRepository.findByCanalAndRemitenteId(CanalBot.OUT, remitente);

        if (existente.isPresent()) {
            SesionBot sesion = existente.get();
            if (sesion.getExpiraEn().isAfter(ZonedDateTime.now())) {
                return sesion;
            }
            // Caducada: se reutiliza la fila para no chocar con el unico del remitente.
            log.info("⌛ Sesion de {} caducada, se reinicia la conversacion.", remitente);
            sesion.setPasoActual(PasoConversacionEnum.INICIO.name());
            sesion.setCarritoJson(CarritoBot.vacio().instantanea());
            return sesion;
        }

        return SesionBot.builder()
                .canal(CanalBot.OUT)
                .remitenteId(remitente)
                .cliente(buscarCliente(remitente).orElse(null))
                .pasoActual(PasoConversacionEnum.INICIO.name())
                .carritoJson(CarritoBot.vacio().instantanea())
                .expiraEn(ZonedDateTime.now().plusHours(HORAS_VIGENCIA_SESION))
                .createdBy(nombre == null || nombre.isBlank() ? "BOT" : nombre)
                .build();
    }

    private void guardar(SesionBot sesion, PasoConversacionEnum paso, CarritoBot carrito) {
        sesion.setPasoActual(paso.name());
        sesion.setCarritoJson(carrito.instantanea());
        sesion.setExpiraEn(ZonedDateTime.now().plusHours(HORAS_VIGENCIA_SESION));
        sesionRepository.save(sesion);
    }

    private void reiniciar(SesionBot sesion) {
        guardar(sesion, PasoConversacionEnum.INICIO, CarritoBot.vacio());
    }

    // =====================================================================
    // Formato
    // =====================================================================

    private static long contarCategorias(List<PlatilloDisponibleDto> platillos) {
        return platillos.stream().map(PlatilloDisponibleDto::getCategoriaId).distinct().count();
    }

    private static String soles(BigDecimal monto) {
        return monto == null ? "S/ 0.00" : "S/ " + monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String etiquetaPago(TipoPagoEnum tipoPago) {
        return switch (tipoPago) {
            case EFECTIVO -> "Efectivo";
            case E_WALLET -> "Yape / Plin";
            case TARJETA -> "Tarjeta";
        };
    }

    /** Los ocho primeros caracteres del UUID bastan para que el cliente lo dicte por telefono. */
    private static String cortoDe(UUID id) {
        return id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
