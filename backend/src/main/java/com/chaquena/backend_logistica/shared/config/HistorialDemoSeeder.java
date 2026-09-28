package com.chaquena.backend_logistica.shared.config;

import com.chaquena.backend_logistica.auth.domain.Cargo;
import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.repository.CargoRepository;
import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.clientes.domain.Cliente;
import com.chaquena.backend_logistica.clientes.repository.ClienteRepository;
import com.chaquena.backend_logistica.pedidos.domain.CanalOrigenEnum;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.Catalogo;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.ClienteDemo;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.ComandaPlan;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.Direccion;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.LineaPlan;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.MesaDemo;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.Plato;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.Promo;
import com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.Reparto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Types;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.LIMA;
import static com.chaquena.backend_logistica.shared.config.demo.PlanDiaDemo.uuid;

/**
 * El historial de demostracion: sesenta dias de ventas, encuestas, turnos,
 * marcaciones y reservas, para que los reportes, el tablero, el desempeno del
 * personal y la fidelizacion tengan algo que mostrar.
 *
 * <p>Corre despues de {@link DatosDemoSeeder} y, a diferencia de el, en cada
 * arranque: rellena lo que falte hasta la hora actual, asi «Hoy» nunca esta en
 * cero en una base de demostracion. No duplica nada porque cada fila tiene un id
 * que sale de su fecha y su posicion ({@link PlanDiaDemo}): lo que ya esta se
 * reconoce y se salta. De hoy solo entra lo que ya termino —cobrado, cancelado,
 * calificado—; nada queda en cocina, en caja ni ocupando una mesa.
 *
 * <p>Se escribe directo con JDBC y no por los servicios, a proposito: dos mil
 * ventas por los servicios agotarian el stock y llenarian el kardex de salidas.
 * El inventario queda como esta. Los importes salen de precio por cantidad, como
 * en el servidor, y las encuestas suman puntos y emiten cupones con la misma
 * regla que la caja.
 *
 * <p>Tambien corrige las tildes de los datos de demostracion que se sembraron
 * sin ellas, solo donde el texto es exactamente el de la demo.
 *
 * <p>Nunca en produccion: depende de {@code app.seed.demo}, que ahi va fijo en
 * false.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.seed.demo", havingValue = "true")
public class HistorialDemoSeeder {

    /** Autor de todo lo que siembra: se distingue de lo que prueba una persona. */
    static final String AUTOR = "DEMO_HISTORIAL";
    static final int DIAS = 60;
    static final int DIAS_ADELANTE = 7;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate jdbcConNombres;
    private final PlatformTransactionManager transacciones;
    private final TrabajadorRepository trabajadorRepository;
    private final CargoRepository cargoRepository;
    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    @Order(3) // despues de DatosIniciales (1) y DatosDemo (2)
    public ApplicationRunner sembrarHistorialDemo() {
        return args -> ejecutar();
    }

    void ejecutar() {
        Integer platillos = jdbc.queryForObject("select count(*) from platillos", Integer.class);
        if (platillos == null || platillos == 0) {
            log.info("Sin carta de demostracion; no se siembra historial.");
            return;
        }

        TransactionTemplate enTransaccion = new TransactionTemplate(transacciones);
        enTransaccion.executeWithoutResult(s -> corregirTildes());
        asegurarMozos();
        asegurarClientes();

        Catalogo catalogo = leerCatalogo();
        ZonedDateTime ahora = ZonedDateTime.now(LIMA);
        LocalDate hoy = ahora.toLocalDate();
        Fidelizacion fidelizacion = new Fidelizacion();

        int comandas = 0;
        for (LocalDate dia = hoy.minusDays(DIAS); !dia.isAfter(hoy); dia = dia.plusDays(1)) {
            LocalDate estos = dia;
            // Un dia por transaccion: si algo falla, ese dia entero se reintenta
            // en el proximo arranque y los anteriores quedan.
            Integer nuevas = enTransaccion.execute(s -> sembrarDia(estos, catalogo, ahora, fidelizacion));
            comandas += nuevas != null ? nuevas : 0;
        }

        int[] personal = enTransaccion.execute(s -> sembrarTurnosYMarcaciones(hoy, ahora));
        int reservas = enTransaccion.execute(s -> sembrarReservas(hoy, ahora, catalogo.mesas()));

        log.info("Historial de demostracion al dia: {} comandas nuevas, {} turnos, {} marcaciones, "
                + "{} reservas, {} cupones.", comandas, personal[0], personal[1], reservas,
                fidelizacion.cuponesEmitidos);
    }

    // =========================================================================
    // Tildes
    // =========================================================================

    /**
     * Los datos de la demo se sembraron sin tildes. Se corrigen solo donde el
     * texto es exactamente el sembrado: lo que alguien ya cambio a mano no se toca.
     */
    private void corregirTildes() {
        String[][] cambios = {
                {"update mesas set zona = ? where zona = ?", "Salón principal", "Salon principal"},
                {"update platillos set nombre = ? where nombre = ?", "Ceviche Clásico", "Ceviche Clasico"},
                {"update platillos set nombre = ? where nombre = ?", "Ají de Gallina", "Aji de Gallina"},
                {"update platillos set nombre = ? where nombre = ?", "Papa a la Huancaína", "Papa a la Huancaina"},
                {"update platillos set nombre = ? where nombre = ?", "Chicharrón de Pescado", "Chicharron de Pescado"},
                {"update platillos set descripcion = ? where descripcion = ?",
                        "Crema de ají amarillo con pollo deshilachado", "Crema de aji amarillo con pollo deshilachado"},
                {"update platillos set descripcion = ? where descripcion = ?",
                        "Papa amarilla con salsa huancaína", "Papa amarilla con salsa huancaina"},
                {"update insumos set nombre = ? where nombre = ?", "Limón", "Limon"},
                {"update insumos set nombre = ? where nombre = ?", "Ají amarillo", "Aji amarillo"},
                {"update insumos set nombre = ? where nombre = ?", "Helado de lúcuma", "Helado de lucuma"},
                {"update complementos_platillo set nombre = ? where nombre = ?", "Helado de lúcuma", "Helado de lucuma"},
                {"update complementos_platillo set nombre = ? where nombre = ?",
                        "Porción extra de arroz", "Porcion extra de arroz"},
                {"update promociones set nombre = ? where nombre = ?", "Menú del día con gaseosa", "Menu del dia con gaseosa"},
                {"update proveedores set nombre = ? where nombre = ?", "Avícola del Sur", "Avicola del Sur"},
                {"update personas set apellidos = ? where apellidos = ? and dni = '70000002'", "Huamán", "Huaman"},
                {"update personas set apellidos = ? where apellidos = ? and dni = '48123456'",
                        "Ramos Cárdenas", "Ramos Cardenas"},
                {"update clientes set direccion_habitual = ? where direccion_habitual = ?",
                        "Av. Ejército 512, Yanahuara", "Av. Ejercito 512, Yanahuara"},
                {"update calificaciones_feedback set comentario = ? where comentario = ?",
                        "El chaufa estuvo en su punto, llegó rápido.", "El chaufa estuvo en su punto, llego rapido."},
        };
        int filas = 0;
        for (String[] c : cambios) {
            filas += jdbc.update(c[0], c[1], c[2]);
        }
        if (filas > 0) log.info("Tildes corregidas en {} filas de la demostracion.", filas);
    }

    // =========================================================================
    // Personal y clientes
    // =========================================================================

    /** Tres mozos con ritmos distintos: sin eso, «ventas por mozo» es una sola barra. */
    private void asegurarMozos() {
        trabajador("mozo2", "Carlos", "Apaza Ríos", "mozo2@chaquena.pe", "51900000007", "70000007");
        trabajador("mozo3", "Lucía", "Condori Paz", "mozo3@chaquena.pe", "51900000008", "70000008");
    }

    private void trabajador(String username, String nombres, String apellidos, String correo,
            String celular, String dni) {
        if (trabajadorRepository.findByUsername(username).isPresent()) return;
        Cargo mozo = cargoRepository.findByNombreIgnoreCase("MOZO").orElse(null);
        if (mozo == null) return;
        trabajadorRepository.saveAndFlush(Trabajador.builder()
                .dni(dni).nombres(nombres).apellidos(apellidos)
                .correo(correo).celular(celular)
                .cargo(mozo)
                .username(username)
                .passwordHash(passwordEncoder.encode(DatosDemoSeeder.CLAVE_DEMO))
                .activo(true)
                .createdBy("SEED_DEMO")
                .build());
    }

    /** Un cliente de la demo: los que tienen punto piden delivery ahi. `peso`: cuanto vuelve. */
    private record ClienteSemilla(String dni, String nombres, String apellidos, String celular,
            String correo, String direccion, Double latitud, Double longitud, int peso) {
    }

    private static final List<ClienteSemilla> CLIENTES = List.of(
            new ClienteSemilla("45000001", "Ana Lucía", "Torres Vega", "51987100001", "ana.torres@correo.pe",
                    "Av. Arequipa 2450, Lince", -12.0868, -77.0345, 9),
            new ClienteSemilla("45000002", "Jorge Luis", "Paredes Rojas", "51987100002", null,
                    "Jr. Huiracocha 1320, Jesús María", -12.0762, -77.0461, 7),
            new ClienteSemilla("45000003", "María Fernanda", "Salazar Díaz", "51987100003",
                    "mafe.salazar@correo.pe", null, null, null, 6),
            new ClienteSemilla("45000004", "Luis Alberto", "Chávez Ramos", "51987100004", null,
                    "Calle Manuel Segura 250, Lince", -12.0853, -77.0371, 5),
            new ClienteSemilla("45000005", "Carmen Rosa", "Huamán Quispe", "51987100005", null,
                    null, null, null, 5),
            new ClienteSemilla("45000006", "Diego Alonso", "Castillo Neyra", "51987100006", "diego.castillo@correo.pe",
                    "Av. Petit Thouars 3350, San Isidro", -12.1003, -77.0330, 4),
            new ClienteSemilla("45000007", "Patricia Elena", "Gutiérrez León", "51987100007", null,
                    null, null, null, 3),
            new ClienteSemilla("45000008", "Renzo Martín", "Vílchez Soto", "51987100008", null,
                    "Av. Brasil 1850, Jesús María", -12.0748, -77.0527, 3),
            new ClienteSemilla("45000009", "Gabriela Sofía", "Medina Arce", "51987100009", "gaby.medina@correo.pe",
                    null, null, null, 2),
            new ClienteSemilla("45000010", "Miguel Ángel", "Ccori Mamani", "51987100010", null,
                    "Jr. Pumacahua 2210, Lince", -12.0851, -77.0402, 2),
            new ClienteSemilla("45000011", "Valeria Alejandra", "Ríos Palomino", "51987100011", null,
                    null, null, null, 2),
            new ClienteSemilla("45000012", "César Augusto", "Delgado Poma", "51987100012", null,
                    "Av. Salaverry 2015, San Isidro", -12.0914, -77.0480, 1),
            new ClienteSemilla("45000013", "Lucía Beatriz", "Zapata Ortiz", "51987100013", "lucia.zapata@correo.pe",
                    null, null, null, 1),
            new ClienteSemilla("45000014", "Andrés Felipe", "Morales Cueto", "51987100014", null,
                    "Calle Los Pinos 145, Miraflores", -12.1189, -77.0296, 1),
            new ClienteSemilla("45000015", "Rocío del Pilar", "Flores Tapia", "51987100015", null,
                    null, null, null, 1));

    /** Los clientes que ya sembro la demo base, con cuanto vuelven. */
    private static final Map<String, Integer> PESO_CLIENTES_BASE = Map.of(
            "41258963", 6, "09876543", 5, "ANON-DEMO0001", 2);

    private void asegurarClientes() {
        for (ClienteSemilla c : CLIENTES) {
            if (clienteRepository.findByDni(c.dni()).isPresent()) continue;
            clienteRepository.saveAndFlush(Cliente.builder()
                    .dni(c.dni()).nombres(c.nombres()).apellidos(c.apellidos())
                    .correo(c.correo()).celular(c.celular())
                    .direccionHabitual(c.direccion())
                    .latitud(c.latitud()).longitud(c.longitud())
                    .puntosFidelidad(0)
                    .scoreFraude(0)
                    .bloqueadoPorFraude(false)
                    .createdBy("SEED_DEMO")
                    .build());
        }
    }

    // =========================================================================
    // Catalogo
    // =========================================================================

    /** Destinos de delivery en los distritos cercanos, con su punto aproximado. */
    private static final List<Direccion> DIRECCIONES = List.of(
            new Direccion("Av. Arequipa 2450, Lince", -12.0868, -77.0345),
            new Direccion("Jr. Huiracocha 1320, Jesús María", -12.0762, -77.0461),
            new Direccion("Av. Salaverry 2015, San Isidro", -12.0914, -77.0480),
            new Direccion("Calle Los Pinos 145, Miraflores", -12.1189, -77.0296),
            new Direccion("Av. Petit Thouars 3350, San Isidro", -12.1003, -77.0330),
            new Direccion("Jr. Tacna 580, Cercado de Lima", -12.0470, -77.0357),
            new Direccion("Av. Brasil 1850, Jesús María", -12.0748, -77.0527),
            new Direccion("Calle Manuel Segura 250, Lince", -12.0853, -77.0371),
            new Direccion("Av. José Gálvez Barrenechea 670, San Isidro", -12.0955, -77.0212),
            new Direccion("Jr. Pumacahua 2210, Lince", -12.0851, -77.0402),
            new Direccion("Av. Aviación 2890, San Borja", -12.0921, -77.0012),
            new Direccion("Calle Schell 320, Miraflores", -12.1215, -77.0298),
            new Direccion("Av. Tomás Marsano 1450, Surquillo", -12.1126, -77.0137),
            new Direccion("Jr. Washington 1250, Cercado de Lima", -12.0579, -77.0405),
            new Direccion("Av. Sucre 820, Pueblo Libre", -12.0772, -77.0643),
            new Direccion("Av. Javier Prado Oeste 1720, Magdalena del Mar", -12.0932, -77.0612));

    private Catalogo leerCatalogo() {
        List<Plato> platos = jdbc.query(
                "select id, nombre, precio_venta_base from platillos where activo order by nombre",
                (rs, i) -> new Plato(rs.getObject("id", UUID.class), rs.getBigDecimal("precio_venta_base"),
                        pesoDe(rs.getString("nombre"))));
        List<PlanDiaDemo.Extra> extras = jdbc.query(
                "select id, precio_adicional from complementos_platillo where activo order by nombre",
                (rs, i) -> new PlanDiaDemo.Extra(rs.getObject("id", UUID.class), rs.getBigDecimal("precio_adicional")));
        List<MesaDemo> mesas = jdbc.query(
                "select id, numero, coalesce(capacidad, 4) as capacidad from mesas where activa order by numero",
                (rs, i) -> new MesaDemo(rs.getObject("id", UUID.class), rs.getString("numero"), rs.getInt("capacidad")));
        List<Promo> promos = jdbc.query(
                "select id, porcentaje_descuento, monto_descuento from promociones where activa order by nombre",
                (rs, i) -> new Promo(rs.getObject("id", UUID.class), rs.getBigDecimal("porcentaje_descuento"),
                        rs.getBigDecimal("monto_descuento")));
        Promo almuerzo = promos.stream().filter(p -> p.monto().signum() > 0).findFirst().orElse(null);
        Promo menu = promos.stream().filter(p -> p.porcentaje().signum() > 0).findFirst().orElse(null);

        List<UUID> mozos = new ArrayList<>();
        for (String usuario : List.of("mozo1", "mozo2", "mozo3")) {
            idDeTrabajador(usuario).ifPresent(mozos::add);
        }
        UUID cajero = idDeTrabajador("caja1").orElse(null);

        Map<String, Integer> pesos = new HashMap<>(PESO_CLIENTES_BASE);
        CLIENTES.forEach(c -> pesos.put(c.dni(), c.peso()));
        List<ClienteDemo> clientes = jdbcConNombres.query("""
                select c.persona_id, p.dni, c.direccion_habitual, c.latitud, c.longitud
                from clientes c join personas p on p.id = c.persona_id
                where p.dni in (:dnis) and not c.bloqueado_por_fraude
                order by p.dni
                """, Map.of("dnis", pesos.keySet()),
                (rs, i) -> new ClienteDemo(rs.getObject("persona_id", UUID.class), rs.getString("direccion_habitual"),
                        (Double) rs.getObject("latitud"), (Double) rs.getObject("longitud"),
                        pesos.getOrDefault(rs.getString("dni"), 1)));

        List<Reparto> repartos = jdbc.query("""
                select v.transportista_id, v.id from vehiculos v
                join transportistas t on t.persona_id = v.transportista_id
                where v.activo and t.activo order by v.placa
                """, (rs, i) -> new Reparto(rs.getObject("transportista_id", UUID.class), rs.getObject("id", UUID.class)));

        BigDecimal igv = jdbc.query("select porcentaje_igv from datos_local where id = 1",
                rs -> rs.next() ? rs.getBigDecimal(1) : new BigDecimal("18.00"));

        return new Catalogo(platos, extras, mesas, almuerzo, menu, mozos, clientes, repartos, cajero,
                DIRECCIONES, igv);
    }

    private java.util.Optional<UUID> idDeTrabajador(String usuario) {
        return jdbc.query("select persona_id from trabajadores where username = ?",
                rs -> rs.next() ? java.util.Optional.of(rs.getObject(1, UUID.class)) : java.util.Optional.<UUID>empty(),
                usuario);
    }

    /** Lo que mas sale de la carta de la demo; un plato nuevo, lo normal. */
    static int pesoDe(String nombre) {
        String n = Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        if (n.contains("lomo")) return 10;
        if (n.contains("ceviche")) return 8;
        if (n.contains("chaufa")) return 7;
        if (n.contains("aji de gallina")) return 6;
        if (n.contains("huancaina") || n.contains("chicharron")) return 5;
        return 3;
    }

    /** Dos dias de cada sesenta, la caja detecta un billete falso en una cena. */
    static boolean diaConFraude(LocalDate dia) {
        return Math.floorMod(dia.toEpochDay(), 29) == 7;
    }

    // =========================================================================
    // Comandas
    // =========================================================================

    private static final String INSERT_ORDEN = """
            insert into ordenes (id, canal_origen, codigo_otp_entrega, created_by, date_created,
              direccion_delivery, estado, flag_cierre_despacho, flag_cierre_platillo, flag_cierre_recepcion,
              last_date_modified, mesa_numero, modified_by, monto_descuento, monto_subtotal, monto_total,
              motivo_cancelacion, mozo_id, scoring_riesgo_orden, tiempo_cierre_despacho, tiempo_cierre_platillo,
              tiempo_cierre_recepcion, tiempo_estimado_cocina_minutos, tiempo_fin_global, tiempo_inicio_cocina,
              tiempo_inicio_global, tipo_orden, tipo_pago, cliente_id, mesa_id, promocion_id, porcentaje_igv,
              latitud_delivery, longitud_delivery)
            values (?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?,?, ?,?,?,?,?, ?,?,?,?, ?,?,?,?,?,?,?, ?,?)
            """;
    private static final int[] TIPOS_ORDEN = {
            Types.OTHER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE,
            Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN, Types.BOOLEAN, Types.BOOLEAN,
            Types.TIMESTAMP_WITH_TIMEZONE, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
            Types.VARCHAR, Types.OTHER, Types.INTEGER, Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE,
            Types.TIMESTAMP_WITH_TIMEZONE, Types.INTEGER, Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE,
            Types.TIMESTAMP_WITH_TIMEZONE, Types.VARCHAR, Types.VARCHAR, Types.OTHER, Types.OTHER, Types.OTHER,
            Types.NUMERIC, Types.DOUBLE, Types.DOUBLE};

    private static final String INSERT_LINEA = """
            insert into orden_detalles (id, cantidad, created_by, date_created, excepciones_nota,
              last_date_modified, modified_by, monto_subtotal, precio_venta_producto, orden_id, platillo_id,
              listo, tiempo_listo)
            values (?,?,?,?,?, ?,?,?,?,?,?, ?,?)
            """;
    private static final int[] TIPOS_LINEA = {
            Types.OTHER, Types.INTEGER, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.VARCHAR,
            Types.TIMESTAMP_WITH_TIMEZONE, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC, Types.OTHER, Types.OTHER,
            Types.BOOLEAN, Types.TIMESTAMP_WITH_TIMEZONE};

    private static final String INSERT_EXTRA = """
            insert into orden_detalle_complementos (cantidad, created_by, date_created, last_date_modified,
              modified_by, precio_venta_complemento, complemento_id, orden_detalle_id)
            values (?,?,?,?, ?,?,?,?)
            """;
    private static final int[] TIPOS_EXTRA = {
            Types.INTEGER, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE,
            Types.VARCHAR, Types.NUMERIC, Types.OTHER, Types.OTHER};

    private static final String INSERT_PAGO = """
            insert into pagos (id, cajero_id, created_by, date_created, es_fraudulento, estado,
              last_date_modified, modified_by, monto, monto_entregado, observacion, referencia, tipo_pago,
              vuelto, orden_id)
            values (?,?,?,?,?,?, ?,?,?,?,?,?,?, ?,?)
            """;
    private static final int[] TIPOS_PAGO = {
            Types.OTHER, Types.OTHER, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.BOOLEAN, Types.VARCHAR,
            Types.TIMESTAMP_WITH_TIMEZONE, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC, Types.VARCHAR, Types.VARCHAR,
            Types.VARCHAR, Types.NUMERIC, Types.OTHER};

    private static final String INSERT_REPARTO = """
            insert into orden_delivery_info (created_by, date_created, hora_despacho, hora_entrega,
              last_date_modified, modified_by, otp_verificado, tiempo_estimado_minutos, orden_id,
              transportista_id, vehiculo_id)
            values (?,?,?,?, ?,?,?,?,?, ?,?)
            """;
    private static final int[] TIPOS_REPARTO = {
            Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE,
            Types.TIMESTAMP_WITH_TIMEZONE, Types.VARCHAR, Types.BOOLEAN, Types.INTEGER, Types.OTHER,
            Types.OTHER, Types.OTHER};

    private static final String INSERT_ENCUESTA = """
            insert into calificaciones_feedback (comentario, created_by, date_created, last_date_modified,
              modified_by, puntaje_atencion, puntaje_comida, puntaje_lugar, cliente_id, orden_id)
            values (?,?,?,?, ?,?,?,?,?,?)
            """;
    private static final int[] TIPOS_ENCUESTA = {
            Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE,
            Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.OTHER, Types.OTHER};

    private int sembrarDia(LocalDate dia, Catalogo c, ZonedDateTime ahora, Fidelizacion fidelizacion) {
        List<ComandaPlan> plan = PlanDiaDemo.planear(dia, c, diaConFraude(dia)).stream()
                .filter(p -> !p.fin().isAfter(ahora))
                .toList();
        if (plan.isEmpty()) return 0;

        Set<UUID> ya = new HashSet<>(jdbcConNombres.queryForList(
                "select id from ordenes where id in (:ids)",
                Map.of("ids", plan.stream().map(ComandaPlan::id).toList()), UUID.class));
        List<ComandaPlan> nuevas = plan.stream().filter(p -> !ya.contains(p.id())).toList();
        if (nuevas.isEmpty()) return 0;

        List<Object[]> ordenes = new ArrayList<>();
        List<Object[]> lineas = new ArrayList<>();
        List<Object[]> extras = new ArrayList<>();
        List<Object[]> pagos = new ArrayList<>();
        List<Object[]> repartos = new ArrayList<>();
        List<Object[]> encuestas = new ArrayList<>();

        for (ComandaPlan p : nuevas) {
            boolean cancelada = p.estado() == EstadoOrdenEnum.CANCELADO;
            OffsetDateTime inicio = ts(p.inicio());
            OffsetDateTime fin = ts(p.fin());

            ordenes.add(new Object[] {
                    p.id(), p.canal().name(), p.otp(), AUTOR, inicio,
                    p.destino() != null ? p.destino().texto() : null, p.estado().name(),
                    !cancelada, !cancelada, true,
                    fin, p.mesa() != null ? p.mesa().numero() : null, AUTOR,
                    p.descuento(), p.subtotal(), p.total(),
                    p.motivoCancelacion(), p.mozoId(), 0, ts(p.cierreDespacho()), ts(p.cierrePlatillo()),
                    ts(p.cierreRecepcion()), p.estimadoCocina(), fin, ts(p.inicioCocina()),
                    inicio, p.tipo().name(), p.tipoPago().name(), p.clienteId(),
                    p.mesa() != null ? p.mesa().id() : null, p.promocionId(), c.igv(),
                    p.destino() != null ? p.destino().latitud() : null,
                    p.destino() != null ? p.destino().longitud() : null});

            for (LineaPlan l : p.lineas()) {
                lineas.add(new Object[] {
                        l.id(), l.cantidad(), AUTOR, inicio, l.nota(),
                        fin, AUTOR, l.subtotal(), l.precio(), p.id(), l.platoId(),
                        !cancelada, cancelada ? null : ts(p.cierrePlatillo())});
                if (l.extraId() != null) {
                    extras.add(new Object[] {1, AUTOR, inicio, inicio, AUTOR, l.precioExtra(), l.extraId(), l.id()});
                }
            }

            if (p.pago() != null) {
                // Lo que se pago por el bot no pasa por la caja: no tiene cajero.
                boolean enCaja = p.canal() == CanalOrigenEnum.POS || p.pago().fraude();
                pagos.add(new Object[] {
                        p.pago().id(), enCaja ? c.cajeroId() : null, AUTOR, fin, p.pago().fraude(),
                        p.pago().fraude() ? "FRAUDULENTO" : "CONFIRMADO",
                        fin, AUTOR, p.pago().monto(), p.pago().entregado(),
                        p.pago().fraude() ? "Billete de S/ 100 falso detectado en caja" : null,
                        p.pago().referencia(), p.pago().tipo().name(),
                        p.pago().vuelto(), p.id()});
            }

            if (p.horaDespacho() != null && p.reparto() != null) {
                repartos.add(new Object[] {
                        AUTOR, ts(p.cierrePlatillo()), ts(p.horaDespacho()), ts(p.cierreDespacho()),
                        fin, AUTOR, true, p.estimadoReparto(), p.id(),
                        p.reparto().transportistaId(), p.reparto().vehiculoId()});
            }

            if (p.encuesta() != null) {
                encuestas.add(new Object[] {
                        p.encuesta().comentario(), AUTOR, fin, fin, AUTOR,
                        p.encuesta().atencion(), p.encuesta().comida(), p.encuesta().lugar(),
                        p.clienteId(), p.id()});
                if (p.clienteId() != null) fidelizacion.calificar(p.clienteId(), p.fin(), p.id());
            }
        }

        jdbc.batchUpdate(INSERT_ORDEN, ordenes, TIPOS_ORDEN);
        jdbc.batchUpdate(INSERT_LINEA, lineas, TIPOS_LINEA);
        if (!extras.isEmpty()) jdbc.batchUpdate(INSERT_EXTRA, extras, TIPOS_EXTRA);
        if (!pagos.isEmpty()) jdbc.batchUpdate(INSERT_PAGO, pagos, TIPOS_PAGO);
        if (!repartos.isEmpty()) jdbc.batchUpdate(INSERT_REPARTO, repartos, TIPOS_REPARTO);
        if (!encuestas.isEmpty()) jdbc.batchUpdate(INSERT_ENCUESTA, encuestas, TIPOS_ENCUESTA);
        fidelizacion.volcar();
        return nuevas.size();
    }

    private static OffsetDateTime ts(ZonedDateTime momento) {
        return momento != null ? momento.toOffsetDateTime() : null;
    }

    // =========================================================================
    // Puntos y cupones
    // =========================================================================

    private static final String ALFABETO_CUPON = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /**
     * La misma regla que la caja al registrar una calificacion: un punto por
     * encuesta y un cupon cada N calificaciones del cliente, contadas sobre todas
     * las que ya tenia. Se acumula por dia y se vuelca al final de cada uno.
     */
    private final class Fidelizacion {
        private final int cadaCuantas;
        private final BigDecimal porcentaje;
        private final int diasVigencia;
        private final Map<UUID, Long> calificaciones = new HashMap<>();
        private final Set<String> codigos = new HashSet<>();
        private final Map<UUID, Integer> puntos = new HashMap<>();
        private final List<Object[]> cupones = new ArrayList<>();
        int cuponesEmitidos;

        Fidelizacion() {
            Object[] config = jdbc.query("""
                    select calificaciones_para_cupon, porcentaje_descuento_cupon, dias_vigencia_cupon
                    from configuracion_local order by id limit 1
                    """, rs -> rs.next()
                    ? new Object[] {rs.getInt(1), rs.getBigDecimal(2), rs.getInt(3)}
                    : new Object[] {5, new BigDecimal("10.00"), 30});
            this.cadaCuantas = Math.max(1, (Integer) config[0]);
            this.porcentaje = (BigDecimal) config[1];
            this.diasVigencia = (Integer) config[2];
            jdbc.query("select cliente_id, count(*) from calificaciones_feedback where cliente_id is not null "
                    + "group by cliente_id", rs -> {
                        calificaciones.put(rs.getObject(1, UUID.class), rs.getLong(2));
                    });
            codigos.addAll(jdbc.queryForList("select upper(codigo) from cupones", String.class));
        }

        void calificar(UUID cliente, ZonedDateTime cuando, UUID orden) {
            long hechas = calificaciones.merge(cliente, 1L, Long::sum);
            puntos.merge(cliente, 1, Integer::sum);
            if (hechas % cadaCuantas != 0) return;

            Random r = new Random(orden.getMostSignificantBits());
            String codigo;
            do {
                StringBuilder s = new StringBuilder("CHQ-");
                for (int i = 0; i < 6; i++) s.append(ALFABETO_CUPON.charAt(r.nextInt(ALFABETO_CUPON.length())));
                codigo = s.toString();
            } while (!codigos.add(codigo));

            ZonedDateTime vence = cuando.plusDays(diasVigencia);
            cupones.add(new Object[] {
                    uuid("demo-cupon:" + orden), codigo, AUTOR, ts(cuando),
                    "Premio por " + cadaCuantas + " calificaciones",
                    vence.isBefore(ZonedDateTime.now(LIMA)) ? "VENCIDO" : "VIGENTE",
                    ts(cuando), ts(vence), ts(cuando), AUTOR, porcentaje, cliente});
            cuponesEmitidos++;
        }

        void volcar() {
            if (!puntos.isEmpty()) {
                // `clientes` solo guarda los puntos; la auditoria vive en `personas`.
                jdbc.batchUpdate("update clientes set puntos_fidelidad = puntos_fidelidad + ? where persona_id = ?",
                        puntos.entrySet().stream().map(e -> new Object[] {e.getValue(), e.getKey()}).toList(),
                        new int[] {Types.INTEGER, Types.OTHER});
                puntos.clear();
            }
            if (!cupones.isEmpty()) {
                jdbc.batchUpdate("""
                        insert into cupones (id, codigo, created_by, date_created, descripcion, estado,
                          fecha_emision, fecha_vencimiento, last_date_modified, modified_by,
                          porcentaje_descuento, cliente_id)
                        values (?,?,?,?,?,?, ?,?,?,?, ?,?)
                        on conflict (id) do nothing
                        """, cupones, new int[] {
                        Types.OTHER, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.VARCHAR,
                        Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE,
                        Types.TIMESTAMP_WITH_TIMEZONE, Types.VARCHAR, Types.NUMERIC, Types.OTHER});
                cupones.clear();
            }
        }
    }

    // =========================================================================
    // Turnos y marcaciones
    // =========================================================================

    /** El horario fijo de cada puesto y su dia de descanso. */
    private record Horario(String usuario, LocalTime inicio, LocalTime fin, DayOfWeek descanso) {
    }

    private static final List<Horario> HORARIOS = List.of(
            new Horario("mozo1", LocalTime.of(11, 30), LocalTime.of(17, 0), DayOfWeek.WEDNESDAY),
            new Horario("mozo2", LocalTime.of(17, 0), LocalTime.of(22, 30), DayOfWeek.THURSDAY),
            new Horario("mozo3", LocalTime.of(12, 0), LocalTime.of(20, 0), DayOfWeek.TUESDAY),
            new Horario("chef1", LocalTime.of(11, 0), LocalTime.of(22, 0), DayOfWeek.TUESDAY),
            new Horario("caja1", LocalTime.of(11, 30), LocalTime.of(19, 30), DayOfWeek.WEDNESDAY),
            new Horario("almacen1", LocalTime.of(8, 0), LocalTime.of(14, 0), DayOfWeek.SUNDAY),
            new Horario("repartidor1", LocalTime.of(12, 0), LocalTime.of(21, 0), DayOfWeek.THURSDAY));

    /**
     * Turnos de los sesenta dias y de la semana que viene, y las marcaciones de
     * los que ya pasaron: casi siempre a tiempo, a veces tarde, y alguna falta.
     * De hoy solo la marcacion ya cerrada: una entrada abierta de la demo le
     * diria a quien prueba con ese usuario que ya esta dentro.
     *
     * @return {turnos nuevos, marcaciones nuevas}
     */
    private int[] sembrarTurnosYMarcaciones(LocalDate hoy, ZonedDateTime ahora) {
        LocalDate desde = hoy.minusDays(DIAS);
        LocalDate hasta = hoy.plusDays(DIAS_ADELANTE);

        Set<String> conTurno = new HashSet<>();
        jdbc.query("select trabajador_id, fecha from turnos where fecha between ? and ?",
                rs -> {
                    conTurno.add(rs.getObject(1, UUID.class) + "|" + rs.getObject(2, LocalDate.class));
                }, desde, hasta);
        Set<String> conMarca = new HashSet<>();
        jdbc.query("select trabajador_id, entrada from marcaciones where entrada >= ?",
                rs -> {
                    conMarca.add(rs.getObject(1, UUID.class) + "|"
                            + rs.getObject(2, OffsetDateTime.class).atZoneSameInstant(LIMA).toLocalDate());
                }, ts(desde.atStartOfDay(LIMA)));

        List<Object[]> turnos = new ArrayList<>();
        List<Object[]> marcas = new ArrayList<>();
        for (Horario h : HORARIOS) {
            UUID trabajador = idDeTrabajador(h.usuario()).orElse(null);
            if (trabajador == null) continue;
            for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
                if (dia.getDayOfWeek() == h.descanso()) continue;
                String clave = trabajador + "|" + dia;
                if (!conTurno.contains(clave)) {
                    turnos.add(new Object[] {
                            uuid("demo-turno:" + h.usuario() + ":" + dia), AUTOR, ts(ahora), dia,
                            h.fin(), h.inicio(), ts(ahora), AUTOR, null, trabajador});
                }
                if (dia.isAfter(hoy) || conMarca.contains(clave)) continue;

                Random r = new Random(uuid("demo-marca:" + h.usuario() + ":" + dia).getMostSignificantBits());
                if (r.nextDouble() < 0.03) continue; // falto
                ZonedDateTime entrada = dia.atTime(h.inicio()).atZone(LIMA)
                        .plusMinutes(r.nextDouble() < 0.1 ? 12 + r.nextInt(14) : -Math.round(6 + r.nextGaussian() * 3));
                ZonedDateTime salida = dia.atTime(h.fin()).atZone(LIMA).plusMinutes(r.nextInt(21) - 5);
                if (salida.isAfter(ahora)) continue;
                marcas.add(new Object[] {
                        uuid("demo-marca:" + h.usuario() + ":" + dia), AUTOR, ts(entrada), ts(entrada),
                        ts(salida), AUTOR, ts(salida), trabajador});
            }
        }

        if (!turnos.isEmpty()) {
            jdbc.batchUpdate("""
                    insert into turnos (id, created_by, date_created, fecha, fin, inicio, last_date_modified,
                      modified_by, nota, trabajador_id)
                    values (?,?,?,?,?,?,?, ?,?,?) on conflict (id) do nothing
                    """, turnos, new int[] {
                    Types.OTHER, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.DATE, Types.TIME, Types.TIME,
                    Types.TIMESTAMP_WITH_TIMEZONE, Types.VARCHAR, Types.VARCHAR, Types.OTHER});
        }
        if (!marcas.isEmpty()) {
            jdbc.batchUpdate("""
                    insert into marcaciones (id, created_by, date_created, entrada, last_date_modified,
                      modified_by, salida, trabajador_id)
                    values (?,?,?,?,?, ?,?,?) on conflict (id) do nothing
                    """, marcas, new int[] {
                    Types.OTHER, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE,
                    Types.TIMESTAMP_WITH_TIMEZONE, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.OTHER});
        }
        return new int[] {turnos.size(), marcas.size()};
    }

    // =========================================================================
    // Reservas
    // =========================================================================

    private static final List<String> RESERVANTES = List.of(
            "Familia Quispe", "Andrea Solís", "Almuerzo de la oficina", "Cumpleaños de Martha",
            "Juan Carlos Rivas", "Familia Torres", "Sofía Benavides", "Reunión de exalumnos",
            "Pedro y Carmen", "Familia Huamaní", "Almuerzo de negocios", "Karina Salas");

    private static final List<String> NOTAS_RESERVA = List.of(
            "Pidió mesa junto a la ventana", "Trae torta de cumpleaños", "Silla para bebé",
            "Uno de ellos es alérgico al marisco");

    private static final List<LocalTime> HORAS_RESERVA = List.of(
            LocalTime.of(13, 0), LocalTime.of(13, 30), LocalTime.of(20, 0), LocalTime.of(20, 30));

    /**
     * Reservas de los sesenta dias y de la semana que viene. Las pasadas quedan
     * cumplidas (alguna cancelada o sin presentarse); las futuras, confirmadas o
     * pendientes. Una reserva de la demo que se quedo confirmada y ya paso se da
     * por cumplida en el siguiente arranque, como lo haria el salon.
     */
    private int sembrarReservas(LocalDate hoy, ZonedDateTime ahora, List<MesaDemo> mesas) {
        if (mesas.isEmpty()) return 0;

        jdbc.update("""
                update reservas set estado = 'CUMPLIDA', modified_by = ?, last_date_modified = ?
                where created_by = ? and estado in ('PENDIENTE', 'CONFIRMADA')
                  and inicio + make_interval(mins => duracion_minutos) < ?
                """, AUTOR, ts(ahora), AUTOR, ts(ahora));

        List<Object[]> reservas = new ArrayList<>();
        for (LocalDate dia = hoy.minusDays(DIAS); !dia.isAfter(hoy.plusDays(DIAS_ADELANTE)); dia = dia.plusDays(1)) {
            Random r = new Random(dia.toEpochDay() * 13 + 5);
            boolean finDeSemana = dia.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue();
            int cuantas = Math.min((finDeSemana ? 2 : 1) + r.nextInt(3), mesas.size());
            List<MesaDemo> barajadas = new ArrayList<>(mesas);
            Collections.shuffle(barajadas, r);

            for (int k = 0; k < cuantas; k++) {
                MesaDemo mesa = barajadas.get(k);
                ZonedDateTime inicio = dia.atTime(HORAS_RESERVA.get(r.nextInt(HORAS_RESERVA.size()))).atZone(LIMA);
                int duracion = r.nextBoolean() ? 90 : 120;
                double e = r.nextDouble();
                String estado;
                if (inicio.plusMinutes(duracion).isBefore(ahora)) {
                    estado = e < 0.8 ? "CUMPLIDA" : e < 0.9 ? "NO_ASISTIO" : "CANCELADA";
                } else if (inicio.isBefore(ahora)) {
                    estado = "CONFIRMADA";
                } else {
                    estado = e < 0.7 ? "CONFIRMADA" : "PENDIENTE";
                }
                ZonedDateTime pedida = inicio.minusDays(1 + r.nextInt(5));
                if (pedida.isAfter(ahora)) pedida = ahora;
                reservas.add(new Object[] {
                        uuid("demo-reserva:" + dia + ":" + k),
                        "519" + (10_000_000 + r.nextInt(89_999_999)), AUTOR, ts(pedida), duracion, estado,
                        ts(inicio), ts(pedida), AUTOR, RESERVANTES.get(r.nextInt(RESERVANTES.size())),
                        r.nextDouble() < 0.2 ? NOTAS_RESERVA.get(r.nextInt(NOTAS_RESERVA.size())) : null,
                        2 + r.nextInt(Math.max(1, mesa.capacidad() - 1)), mesa.id()});
            }
        }

        int[] hechas = jdbc.batchUpdate("""
                insert into reservas (id, celular, created_by, date_created, duracion_minutos, estado, inicio,
                  last_date_modified, modified_by, nombre, nota, personas, mesa_id)
                values (?,?,?,?,?,?,?, ?,?,?,?,?,?) on conflict (id) do nothing
                """, reservas, new int[] {
                Types.OTHER, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.INTEGER,
                Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.OTHER});
        // Las que ya estaban cuentan 0: `on conflict do nothing` no inserta nada.
        int nuevas = 0;
        for (int n : hechas) nuevas += Math.max(n, 0);
        return nuevas;
    }
}
