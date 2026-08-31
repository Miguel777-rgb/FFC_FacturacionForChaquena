package com.chaquena.backend_logistica.inventario.bot.service;

import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.service.IdentidadBotService;
import com.chaquena.backend_logistica.delivery.bot.parser.CantidadParserService;
import com.chaquena.backend_logistica.inventario.domain.Insumo;
import com.chaquena.backend_logistica.inventario.domain.TipoControlInsumoEnum;
import com.chaquena.backend_logistica.inventario.dto.MovimientoResponseDto;
import com.chaquena.backend_logistica.inventario.repository.InsumoRepository;
import com.chaquena.backend_logistica.inventario.service.InventarioService;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.StockInsuficienteException;
import com.chaquena.backend_logistica.shared.mensajeria.EmisorBotInterno;
import com.chaquena.backend_logistica.shared.mensajeria.MensajeBot;
import com.chaquena.backend_logistica.shared.mensajeria.OpcionBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Control de stock por el bot interno: cocinado, merma y compra.
 *
 * <p>Este servicio no sabe por que proveedor llega ni sale el mensaje. Recibe
 * un {@link MensajeBot} ya normalizado y responde por {@link EmisorBotInterno};
 * el cambio de WhatsApp a Discord no le toco la maquina de estados, solo los
 * limites de la lista, que ahora los pregunta al emisor en vez de darlos por
 * supuestos.
 *
 * <p>El estado vive en Redis con caducidad corta, al contrario que el bot de
 * clientes. Aqui no hay nada que perder si se reinicia el backend: un
 * almacenero a medio registrar una merma vuelve a empezar en quince segundos,
 * mientras que un carrito de cliente a medio llenar es trabajo que no se le
 * puede pedir dos veces.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockBotService {

    private static final String REDIS_PREFIX = "bot:sesion:stock:";
    private static final Duration VIGENCIA_SESION = Duration.ofMinutes(15);

    /** Prefijo de todo lo que este flujo emite; el listener enruta por el. */
    public static final String PREFIJO = "stock:";
    private static final String PREFIJO_OPERACION = PREFIJO + "op:";
    private static final String PREFIJO_INSUMO = PREFIJO + "insumo:";

    private static final String PASO_OPERACION = "SELECCION_OPERACION";
    private static final String PASO_INSUMO = "SELECCION_INSUMO";
    private static final String PASO_CANTIDAD = "INGRESO_CANTIDAD";

    private final InsumoRepository insumoRepository;
    private final InventarioService inventarioService;
    private final CantidadParserService cantidadParserService;
    private final IdentidadBotService identidad;
    private final EmisorBotInterno emisor;
    private final RedisTemplate<String, Object> redisTemplate;

    /** Si este mensaje le corresponde a este flujo. */
    public boolean atiende(String contenido) {
        return contenido != null && contenido.startsWith(PREFIJO);
    }

    /**
     * Si hay una conversacion de stock abierta con este remitente.
     *
     * <p>Lo consulta el listener antes de entregar un texto escrito a mano: una
     * cantidad como "cinco" no lleva prefijo que diga a que flujo pertenece, y
     * el bot interno atiende tres a la vez. La sesion abierta es lo que lo
     * desambigua.
     */
    public boolean tieneSesionAbierta(String remitenteId) {
        return leerSesion(remitenteId) != null;
    }

    /** Abre la conversacion. Lo llama el comando de barra {@code /stock}. */
    public void iniciar(MensajeBot mensaje) {
        Trabajador trabajador;
        try {
            trabajador = identidad.exigirTrabajador(mensaje.remitenteId());
        } catch (ConflictoException e) {
            emisor.enviarTexto(mensaje.remitenteId(), e.getMessage());
            return;
        }

        log.info("📦 [Bot IN] {} abre el control de stock.", trabajador.getUsername());
        ofrecerOperaciones(mensaje.remitenteId());
    }

    /**
     * Continua una conversacion abierta. Es la entrada de todo lo que no sea el
     * comando inicial: la fila elegida de una lista o la cantidad tecleada.
     *
     * <p>Sin {@code @Transactional} a proposito. El movimiento de kardex lo
     * escribe {@link InventarioService} en su propia transaccion; si falla por
     * stock insuficiente, esa transaccion se deshace sola y aqui hace falta
     * seguir vivo para explicarselo al almacenero. Envolviendo esto en una
     * transaccion comun, el rechazo dejaria la de fuera marcada como
     * rollback-only y el commit final reventaria justo despues de haberle dicho
     * al almacenero que todo estaba bien.
     */
    public void procesar(MensajeBot mensaje) {
        String remitente = mensaje.remitenteId();

        Trabajador trabajador;
        try {
            trabajador = identidad.exigirTrabajador(remitente);
        } catch (ConflictoException e) {
            emisor.enviarTexto(remitente, e.getMessage());
            return;
        }

        String contenido = mensaje.contenido() == null ? "" : mensaje.contenido().trim();
        String normalizado = contenido.toLowerCase(Locale.ROOT);

        if (normalizado.equals("cancelar") || normalizado.equals("salir")) {
            borrarSesion(remitente);
            emisor.enviarTexto(remitente, "🚫 Registro cancelado. Escribe `/stock` cuando quieras retomarlo.");
            return;
        }

        Map<String, Object> sesion = leerSesion(remitente);

        if (sesion == null || normalizado.equals("hola") || normalizado.equals("menu")) {
            ofrecerOperaciones(remitente);
            return;
        }

        String paso = (String) sesion.get("pasoActual");
        log.debug("🔄 [Bot IN] {} esta en el paso {}.", trabajador.getUsername(), paso);

        switch (paso == null ? "" : paso) {
            case PASO_OPERACION -> recibirOperacion(remitente, contenido, sesion);
            case PASO_INSUMO -> recibirInsumo(remitente, contenido, sesion);
            case PASO_CANTIDAD -> recibirCantidad(remitente, contenido, sesion, trabajador);
            default -> ofrecerOperaciones(remitente);
        }
    }

    // =====================================================================
    // Pasos
    // =====================================================================

    private void ofrecerOperaciones(String remitente) {
        guardarSesion(remitente, new HashMap<>(Map.of("pasoActual", PASO_OPERACION)));

        List<OpcionBot> opciones = List.of(
                new OpcionBot(PREFIJO_OPERACION + "COCINADO", "Registrar cocinado",
                        "Transforma insumo crudo en cocido"),
                new OpcionBot(PREFIJO_OPERACION + "MERMA", "Registrar merma",
                        "Reporta alimento vencido o dañado"),
                new OpcionBot(PREFIJO_OPERACION + "COMPRA", "Registrar compra",
                        "Ingreso de insumo del proveedor"));

        emisor.enviarOpciones(remitente, "📦 Control de stock",
                "¿Qué operación vas a registrar?", "Ver operaciones", opciones);
    }

    private void recibirOperacion(String remitente, String seleccion, Map<String, Object> sesion) {
        if (!seleccion.startsWith(PREFIJO_OPERACION)) {
            emisor.enviarTexto(remitente, "👆 Elige una operación de la lista, por favor.");
            return;
        }

        sesion.put("tipoOperacion", seleccion.substring(PREFIJO_OPERACION.length()));
        sesion.put("pasoActual", PASO_INSUMO);
        guardarSesion(remitente, sesion);

        List<Insumo> insumos = insumoRepository.findAll();
        if (insumos.isEmpty()) {
            emisor.enviarTexto(remitente, "😕 No hay insumos dados de alta todavía.");
            borrarSesion(remitente);
            return;
        }

        // Discord admite 25 filas por lista y WhatsApp 10. Preguntarselo al
        // emisor evita que el almacenero deje de ver insumos sin enterarse.
        int tope = emisor.maxOpciones();
        List<OpcionBot> opciones = new ArrayList<>();
        for (Insumo insumo : insumos.stream().limit(tope).toList()) {
            opciones.add(new OpcionBot(
                    PREFIJO_INSUMO + insumo.getId(),
                    insumo.getNombre(),
                    "Stock: " + insumo.getStockActual() + " " + insumo.getUnidadMedida()));
        }

        String cuerpo = insumos.size() > tope
                ? "Selecciona el insumo (se muestran los primeros " + tope + " de " + insumos.size() + "):"
                : "Selecciona el insumo a modificar:";

        emisor.enviarOpciones(remitente, "🥔 Selección de insumo", cuerpo, "Ver insumos", opciones);
    }

    private void recibirInsumo(String remitente, String seleccion, Map<String, Object> sesion) {
        if (!seleccion.startsWith(PREFIJO_INSUMO)) {
            emisor.enviarTexto(remitente, "👆 Elige un insumo de la lista, por favor.");
            return;
        }

        sesion.put("insumoId", seleccion.substring(PREFIJO_INSUMO.length()));
        sesion.put("pasoActual", PASO_CANTIDAD);
        guardarSesion(remitente, sesion);

        emisor.enviarTexto(remitente,
                "🔢 Escribe la cantidad (por ejemplo `10`, `5.5` o `cinco`).\n"
                        + "Escribe `cancelar` para dejarlo.");
    }

    private void recibirCantidad(String remitente, String texto, Map<String, Object> sesion,
            Trabajador trabajador) {

        Optional<Integer> cantidadOpt = cantidadParserService.parsearCantidad(texto);
        if (cantidadOpt.isEmpty()) {
            emisor.enviarTexto(remitente, "❌ No entendí esa cantidad. Escribe un número, por ejemplo `5`.");
            return;
        }

        BigDecimal cantidad = BigDecimal.valueOf(cantidadOpt.get());
        UUID insumoId = UUID.fromString((String) sesion.get("insumoId"));
        String operacion = (String) sesion.get("tipoOperacion");

        TipoControlInsumoEnum tipoControl;
        BigDecimal delta;

        if ("MERMA".equals(operacion)) {
            tipoControl = TipoControlInsumoEnum.MERMA_DESPERDICIO;
            delta = cantidad.negate();
        } else {
            tipoControl = "COCINADO".equals(operacion)
                    ? TipoControlInsumoEnum.TRANSFORMACION_COCIDO
                    : TipoControlInsumoEnum.ENTRADA_COMPRA;
            delta = cantidad;
        }

        // El bot no escribe stock por su cuenta: pasa por el mismo servicio que
        // el POS, que toma bloqueo sobre el insumo y deja la linea de kardex.
        // Antes tenia su propia ruta de escritura y una merma por chat podia
        // pisar el descuento de una comanda simultanea.
        MovimientoResponseDto movimiento;
        try {
            movimiento = inventarioService.registrarMovimientoInterno(insumoId, tipoControl, delta,
                    "Registro via bot por trabajador: " + trabajador.getUsername(),
                    trabajador.getId(), trabajador.getUsername());
        } catch (StockInsuficienteException e) {
            emisor.enviarTexto(remitente,
                    "❌ No se pudo registrar: el movimiento dejaría el stock en negativo.\n" + e.getMessage());
            return;
        }

        borrarSesion(remitente);

        emisor.enviarTexto(remitente, String.format(
                "✅ **Registro en kardex**%n%nInsumo: %s%nOperación: %s%nCantidad: %s %s%n"
                        + "Stock anterior: %s%nStock nuevo: %s%nRegistrado por: %s",
                movimiento.getInsumoNombre(), tipoControl, cantidad, movimiento.getUnidadMedida(),
                movimiento.getStockAnterior(), movimiento.getStockNuevo(), trabajador.getNombres()));
    }

    // =====================================================================
    // Sesion en Redis
    // =====================================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> leerSesion(String remitente) {
        try {
            return (Map<String, Object>) redisTemplate.opsForValue().get(REDIS_PREFIX + remitente);
        } catch (Exception e) {
            // Redis caido no puede dejar mudo al bot: se trata como sesion nueva.
            log.error("⚠️ Redis no respondio al leer la sesion de stock: {}", e.getMessage());
            return null;
        }
    }

    private void guardarSesion(String remitente, Map<String, Object> sesion) {
        try {
            redisTemplate.opsForValue().set(REDIS_PREFIX + remitente, sesion, VIGENCIA_SESION);
        } catch (Exception e) {
            log.error("❌ No se pudo guardar la sesion de stock en Redis: {}", e.getMessage());
        }
    }

    private void borrarSesion(String remitente) {
        try {
            redisTemplate.delete(REDIS_PREFIX + remitente);
        } catch (Exception e) {
            log.error("❌ No se pudo borrar la sesion de stock en Redis: {}", e.getMessage());
        }
    }
}
