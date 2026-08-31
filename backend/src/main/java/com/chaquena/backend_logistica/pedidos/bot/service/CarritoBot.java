package com.chaquena.backend_logistica.pedidos.bot.service;

import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoPagoEnum;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vista tipada del carrito que arman los bots antes de convertirlo en comanda.
 *
 * <p>Lo comparten el bot de clientes, que lo guarda en
 * {@code sesiones_bot.carrito_json}, y el bot del mozo, que lo guarda en Redis.
 * Los dos almacenes tienen el mismo defecto y por eso la clase les sirve a
 * ambos.
 *
 * <p>La columna es {@code jsonb} y Hibernate la entrega como un
 * {@code Map<String, Object>} crudo, donde al releer no sobrevive ni el tipo
 * {@link UUID} ni el {@link BigDecimal}: un identificador vuelve como cadena y
 * un importe como {@code Double}, con el redondeo binario que eso arrastra. Por
 * eso todo se escribe como texto y se reconstruye al leer. Esta clase es el
 * unico sitio que conoce esa conversion; el resto del bot trabaja con tipos
 * reales.
 */
public class CarritoBot {

    private static final String CLAVE_ITEMS = "items";
    private static final String CLAVE_TIPO_ORDEN = "tipoOrden";
    private static final String CLAVE_DIRECCION = "direccion";
    private static final String CLAVE_TIPO_PAGO = "tipoPago";
    private static final String CLAVE_MESA = "mesaId";

    private final Map<String, Object> raiz;

    private CarritoBot(Map<String, Object> raiz) {
        this.raiz = raiz;
    }

    public static CarritoBot vacio() {
        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put(CLAVE_ITEMS, new ArrayList<Map<String, Object>>());
        return new CarritoBot(raiz);
    }

    @SuppressWarnings("unchecked")
    public static CarritoBot de(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return vacio();
        }
        Map<String, Object> raiz = new LinkedHashMap<>(json);
        raiz.putIfAbsent(CLAVE_ITEMS, new ArrayList<Map<String, Object>>());
        // La lista puede venir inmutable desde el deserializador de jsonb.
        raiz.put(CLAVE_ITEMS, new ArrayList<>((List<Map<String, Object>>) raiz.get(CLAVE_ITEMS)));
        return new CarritoBot(raiz);
    }

    public Map<String, Object> comoJson() {
        return raiz;
    }

    // ---------------- Items ----------------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items() {
        return (List<Map<String, Object>>) raiz.get(CLAVE_ITEMS);
    }

    public boolean vacioDeItems() {
        return items().isEmpty();
    }

    public int cantidadDeItems() {
        return items().size();
    }

    /**
     * Abre un platillo nuevo. Queda "en curso" hasta que el cliente termine de
     * elegirle complementos y nota; mientras tanto es siempre el ultimo de la
     * lista.
     */
    public void agregarPlatillo(UUID platilloId, String nombre, BigDecimal precio) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("platilloId", platilloId.toString());
        item.put("nombre", nombre);
        item.put("precio", precio.toPlainString());
        item.put("cantidad", 1);
        item.put("complementos", new ArrayList<Map<String, Object>>());
        items().add(item);
    }

    private Map<String, Object> enCurso() {
        List<Map<String, Object>> items = items();
        if (items.isEmpty()) {
            throw new IllegalStateException("No hay ningun platillo en curso en el carrito.");
        }
        return items.get(items.size() - 1);
    }

    public boolean hayPlatilloEnCurso() {
        return !items().isEmpty();
    }

    public String nombreEnCurso() {
        return (String) enCurso().get("nombre");
    }

    @SuppressWarnings("unchecked")
    public void agregarComplementoAlEnCurso(UUID complementoId, String nombre, BigDecimal precio) {
        Map<String, Object> complemento = new LinkedHashMap<>();
        complemento.put("complementoId", complementoId.toString());
        complemento.put("nombre", nombre);
        complemento.put("precio", precio.toPlainString());
        ((List<Map<String, Object>>) enCurso().get("complementos")).add(complemento);
    }

    public void ponerNotaAlEnCurso(String nota) {
        if (nota != null && !nota.isBlank()) {
            enCurso().put("nota", nota.trim());
        }
    }

    public List<ItemCarrito> lineas() {
        return items().stream().map(CarritoBot::aItem).toList();
    }

    @SuppressWarnings("unchecked")
    private static ItemCarrito aItem(Map<String, Object> item) {
        List<ComplementoCarrito> complementos =
                ((List<Map<String, Object>>) item.getOrDefault("complementos", List.of())).stream()
                        .map(c -> new ComplementoCarrito(
                                UUID.fromString((String) c.get("complementoId")),
                                (String) c.get("nombre"),
                                new BigDecimal((String) c.get("precio"))))
                        .toList();

        return new ItemCarrito(
                UUID.fromString((String) item.get("platilloId")),
                (String) item.get("nombre"),
                new BigDecimal((String) item.get("precio")),
                ((Number) item.getOrDefault("cantidad", 1)).intValue(),
                (String) item.get("nota"),
                complementos);
    }

    /**
     * Total estimado que se le muestra al cliente antes de confirmar. El importe
     * que vale es el que calcula el modulo de pedidos al crear la comanda, con
     * promociones incluidas; este solo sirve para que el resumen del chat no
     * llegue mudo de precios.
     */
    public BigDecimal totalEstimado() {
        return lineas().stream()
                .map(ItemCarrito::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------- Datos de cierre ----------------

    public void ponerTipoOrden(TipoOrdenEnum tipoOrden) {
        raiz.put(CLAVE_TIPO_ORDEN, tipoOrden.name());
    }

    public TipoOrdenEnum tipoOrden() {
        Object valor = raiz.get(CLAVE_TIPO_ORDEN);
        return valor == null ? null : TipoOrdenEnum.valueOf((String) valor);
    }

    public void ponerDireccion(String direccion) {
        raiz.put(CLAVE_DIRECCION, direccion);
    }

    public String direccion() {
        return (String) raiz.get(CLAVE_DIRECCION);
    }

    public void ponerTipoPago(TipoPagoEnum tipoPago) {
        raiz.put(CLAVE_TIPO_PAGO, tipoPago.name());
    }

    public TipoPagoEnum tipoPago() {
        Object valor = raiz.get(CLAVE_TIPO_PAGO);
        return valor == null ? null : TipoPagoEnum.valueOf((String) valor);
    }

    /** Mesa del salon. Solo la usa el bot del mozo; el cliente no ocupa mesas. */
    public void ponerMesa(UUID mesaId) {
        raiz.put(CLAVE_MESA, mesaId.toString());
    }

    public UUID mesa() {
        Object valor = raiz.get(CLAVE_MESA);
        return valor == null ? null : UUID.fromString((String) valor);
    }

    /** Copia defensiva para dejarla en la sesion sin compartir referencias mutables. */
    public Map<String, Object> instantanea() {
        return new HashMap<>(raiz);
    }

    // ---------------- Proyecciones ----------------

    public record ComplementoCarrito(UUID id, String nombre, BigDecimal precio) {
    }

    public record ItemCarrito(UUID platilloId, String nombre, BigDecimal precio, int cantidad,
            String nota, List<ComplementoCarrito> complementos) {

        public BigDecimal subtotal() {
            BigDecimal complementos = this.complementos.stream()
                    .map(ComplementoCarrito::precio)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return precio.add(complementos).multiply(BigDecimal.valueOf(cantidad));
        }
    }
}
