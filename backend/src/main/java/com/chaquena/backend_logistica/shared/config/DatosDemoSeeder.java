package com.chaquena.backend_logistica.shared.config;

import com.chaquena.backend_logistica.auth.domain.Cargo;
import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.repository.CargoRepository;
import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.clientes.domain.Cliente;
import com.chaquena.backend_logistica.clientes.domain.ClienteEmpresa;
import com.chaquena.backend_logistica.clientes.domain.Empresa;
import com.chaquena.backend_logistica.clientes.repository.ClienteEmpresaRepository;
import com.chaquena.backend_logistica.clientes.repository.ClienteRepository;
import com.chaquena.backend_logistica.clientes.repository.EmpresaRepository;
import com.chaquena.backend_logistica.delivery.domain.TipoVehiculoEnum;
import com.chaquena.backend_logistica.delivery.domain.Transportista;
import com.chaquena.backend_logistica.delivery.domain.Vehiculo;
import com.chaquena.backend_logistica.delivery.repository.TransportistaRepository;
import com.chaquena.backend_logistica.delivery.repository.VehiculoRepository;
import com.chaquena.backend_logistica.inventario.domain.*;
import com.chaquena.backend_logistica.inventario.repository.*;
import com.chaquena.backend_logistica.inventario.service.InventarioService;
import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.domain.Mesa;
import com.chaquena.backend_logistica.mesas.repository.MesaRepository;
import com.chaquena.backend_logistica.pedidos.domain.*;
import com.chaquena.backend_logistica.pedidos.dto.*;
import com.chaquena.backend_logistica.pedidos.service.OrdenService;
import com.chaquena.backend_logistica.pagos.dto.RegistrarPagoRequestDto;
import com.chaquena.backend_logistica.pagos.service.PagoService;
import com.chaquena.backend_logistica.fidelizacion.dto.FeedbackRequestDto;
import com.chaquena.backend_logistica.fidelizacion.service.FidelizacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Datos de demostracion para poder recorrer la aplicacion sin toparse con
 * pantallas y listados vacios. Siembra personal, carta con recetas, stock,
 * mesas, clientes, un transportista y tres comandas en distintos puntos del
 * ciclo, para que el KDS, la caja y los reportes tengan algo que mostrar.
 *
 * Se activa con app.seed.demo=true (o SEED_DEMO=true) y no hace nada si ya
 * hay platillos cargados, asi que es seguro dejarlo encendido en desarrollo.
 * Nunca deberia activarse en produccion.
 *
 * Las altas pasan por los mismos servicios que usa la API, no por SQL
 * directo: asi el stock se descuenta por receta de verdad, el kardex queda
 * poblado y los totales salen del mismo calculo que veria el frontend.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.seed.demo", havingValue = "true")
public class DatosDemoSeeder {

    /** Contrasena unica para todos los usuarios de demostracion. */
    public static final String CLAVE_DEMO = "Chaquena2001";

    private final TrabajadorRepository trabajadorRepository;
    private final CargoRepository cargoRepository;
    private final PasswordEncoder passwordEncoder;
    private final InsumoRepository insumoRepository;
    private final CategoriaPlatilloRepository categoriaRepository;
    private final PlatilloRepository platilloRepository;
    private final ComplementoPlatilloRepository complementoRepository;
    private final PromocionRepository promocionRepository;
    private final MesaRepository mesaRepository;
    private final ClienteRepository clienteRepository;
    private final EmpresaRepository empresaRepository;
    private final ClienteEmpresaRepository clienteEmpresaRepository;
    private final TransportistaRepository transportistaRepository;
    private final VehiculoRepository vehiculoRepository;
    private final InventarioService inventarioService;
    private final OrdenService ordenService;
    private final PagoService pagoService;
    private final FidelizacionService fidelizacionService;

    @Bean
    @Order(2) // despues de DatosInicialesSeeder, que crea roles y cargos
    public ApplicationRunner sembrarDemo() {
        return args -> sembrar();
    }

    @Transactional
    public void sembrar() {
        if (platilloRepository.count() > 0) {
            log.info("Datos de demostracion ya presentes; no se siembra nada.");
            return;
        }

        log.info("Sembrando datos de demostracion...");

        Trabajador admin = sembrarPersonal();
        autenticarComo(admin);
        try {
            Map<String, Insumo> insumos = sembrarInsumos(admin.getId());
            Map<String, Platillo> platillos = sembrarCarta(insumos);
            sembrarComplementosYPromociones(insumos);
            List<Mesa> mesas = sembrarMesas();
            List<Cliente> clientes = sembrarClientes();
            sembrarDelivery();
            sembrarComandas(platillos, mesas, clientes);
        } finally {
            SecurityContextHolder.clearContext();
        }

        log.info("Datos de demostracion listos. Usuarios admin, mozo1, chef1, caja1, "
                + "almacen1 y repartidor1, todos con la clave '{}'. "
                + "Entra por POST /api/v1/auth/login o revisa /swagger-ui.html", CLAVE_DEMO);
    }

    // -----------------------------------------------------------------
    // Personal
    // -----------------------------------------------------------------

    private Trabajador sembrarPersonal() {
        Trabajador admin = crearTrabajador("ADMINISTRADOR", "admin", "Miguel", "Flores",
                "admin@chaquena.pe", "51900000001", "70000001");
        crearTrabajador("MOZO", "mozo1", "Rosa", "Huaman",
                "mozo@chaquena.pe", "51900000002", "70000002");
        crearTrabajador("JEFE DE COCINA", "chef1", "Julio", "Ccahuana",
                "cocina@chaquena.pe", "51900000003", "70000003");
        crearTrabajador("CAJERO", "caja1", "Elena", "Ticona",
                "caja@chaquena.pe", "51900000004", "70000004");
        crearTrabajador("ALMACENERO", "almacen1", "Pedro", "Mamani",
                "almacen@chaquena.pe", "51900000005", "70000005");
        crearTrabajador("REPARTIDOR", "repartidor1", "Diego", "Vargas",
                "reparto@chaquena.pe", "51900000006", "70000006");
        return admin;
    }

    private Trabajador crearTrabajador(String nombreCargo, String username, String nombres,
            String apellidos, String correo, String celular, String dni) {
        return trabajadorRepository.findByUsername(username).orElseGet(() -> {
            Cargo cargo = cargoRepository.findByNombreIgnoreCase(nombreCargo)
                    .orElseThrow(() -> new IllegalStateException(
                            "Falta el cargo " + nombreCargo + "; revisa DatosInicialesSeeder."));
            return trabajadorRepository.saveAndFlush(Trabajador.builder()
                    .dni(dni).nombres(nombres).apellidos(apellidos)
                    .correo(correo).celular(celular)
                    .cargo(cargo)
                    .username(username)
                    .passwordHash(passwordEncoder.encode(CLAVE_DEMO))
                    .activo(true)
                    .createdBy("SEED_DEMO")
                    .build());
        });
    }

    /**
     * Los servicios sellan la auditoria con el usuario del token. Como el
     * seeder corre fuera de una peticion HTTP, se coloca al administrador en
     * el contexto para que created_by y trabajador_id queden con un autor real
     * en lugar de "SYSTEM".
     */
    private void autenticarComo(Trabajador trabajador) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(trabajador.getCorreo(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // -----------------------------------------------------------------
    // Inventario
    // -----------------------------------------------------------------

    private Map<String, Insumo> sembrarInsumos(UUID autorId) {
        Map<String, Insumo> insumos = new java.util.LinkedHashMap<>();

        insumos.put("carne",    insumo(autorId, "Carne de res",             TipoInsumoEnum.NO_COCIDO, "KG",  "5.000",  "25.000"));
        insumos.put("pescado",  insumo(autorId, "Filete de pescado",        TipoInsumoEnum.NO_COCIDO, "KG",  "3.000",  "12.000"));
        insumos.put("pollo",    insumo(autorId, "Pechuga de pollo",         TipoInsumoEnum.NO_COCIDO, "KG",  "5.000",  "20.000"));
        insumos.put("arrozC",   insumo(autorId, "Arroz crudo",              TipoInsumoEnum.NO_COCIDO, "KG", "10.000",  "50.000"));
        insumos.put("arrozK",   insumo(autorId, "Arroz cocido",             TipoInsumoEnum.COCIDO,    "KG",  "2.000",   "8.000"));
        insumos.put("papa",     insumo(autorId, "Papa amarilla",            TipoInsumoEnum.NO_COCIDO, "KG",  "8.000",  "30.000"));
        insumos.put("cebolla",  insumo(autorId, "Cebolla roja",             TipoInsumoEnum.NO_COCIDO, "KG",  "4.000",  "15.000"));
        insumos.put("tomate",   insumo(autorId, "Tomate",                   TipoInsumoEnum.NO_COCIDO, "KG",  "3.000",  "12.000"));
        insumos.put("limon",    insumo(autorId, "Limon",                    TipoInsumoEnum.NO_COCIDO, "KG",  "3.000",  "10.000"));
        insumos.put("aji",      insumo(autorId, "Aji amarillo",             TipoInsumoEnum.NO_COCIDO, "KG",  "1.000",   "5.000"));
        insumos.put("aceite",   insumo(autorId, "Aceite vegetal",           TipoInsumoEnum.NO_COCIDO, "LTR", "5.000",  "20.000"));
        // Deliberadamente por debajo del minimo, para que GET /insumos/alertas
        // devuelva algo y se pueda probar el aviso al jefe de cocina.
        insumos.put("culantro", insumo(autorId, "Culantro",                 TipoInsumoEnum.NO_COCIDO, "KG",  "2.000",   "0.500"));
        insumos.put("gaseosa",  insumo(autorId, "Gaseosa Inca Kola 500 ml", TipoInsumoEnum.NO_COCIDO, "UNIDAD", "12.000", "48.000"));
        insumos.put("cerveza",  insumo(autorId, "Cerveza Pilsen 620 ml",    TipoInsumoEnum.NO_COCIDO, "UNIDAD", "12.000", "36.000"));
        insumos.put("helado",   insumo(autorId, "Helado de lucuma",         TipoInsumoEnum.COCIDO,    "LTR",  "2.000",   "6.000"));

        return insumos;
    }

    private Insumo insumo(UUID autorId, String nombre, TipoInsumoEnum tipo, String unidad,
            String minimo, String stockInicial) {
        Insumo insumo = insumoRepository.saveAndFlush(Insumo.builder()
                .nombre(nombre)
                .tipoInsumo(tipo)
                .unidadMedida(unidad)
                .stockActual(BigDecimal.ZERO)
                .stockMinimo(new BigDecimal(minimo))
                .createdBy("SEED_DEMO")
                .build());

        // El stock entra como movimiento para que el kardex no nazca vacio.
        inventarioService.registrarMovimientoInterno(insumo.getId(),
                TipoControlInsumoEnum.ENTRADA_COMPRA, new BigDecimal(stockInicial),
                "Carga inicial de demostracion", autorId, "SEED_DEMO");

        return insumoRepository.findById(insumo.getId()).orElseThrow();
    }

    // -----------------------------------------------------------------
    // Carta
    // -----------------------------------------------------------------

    private Map<String, Platillo> sembrarCarta(Map<String, Insumo> in) {
        CategoriaPlatillo criollos  = categoria("Criollos", "Platos de fondo de la casa");
        CategoriaPlatillo marinos   = categoria("Marinos", "Pescados y mariscos");
        CategoriaPlatillo entradas  = categoria("Entradas", "Para empezar");
        categoria("Bebidas", "Gaseosas, cervezas y jugos");
        categoria("Postres", "Dulces de la casa");

        Map<String, Platillo> platillos = new java.util.LinkedHashMap<>();

        platillos.put("lomo", platillo(criollos, "Lomo Saltado", "Lomo de res salteado con papas fritas y arroz",
                "32.00", Map.of(in.get("carne"), "0.250", in.get("papa"), "0.200",
                        in.get("cebolla"), "0.080", in.get("tomate"), "0.060",
                        in.get("arrozK"), "0.200", in.get("aceite"), "0.030")));

        platillos.put("ceviche", platillo(marinos, "Ceviche Clasico", "Pescado fresco en leche de tigre",
                "28.00", Map.of(in.get("pescado"), "0.220", in.get("limon"), "0.120",
                        in.get("cebolla"), "0.060", in.get("aji"), "0.020")));

        platillos.put("aji", platillo(criollos, "Aji de Gallina", "Crema de aji amarillo con pollo deshilachado",
                "26.00", Map.of(in.get("pollo"), "0.200", in.get("aji"), "0.040",
                        in.get("arrozK"), "0.200")));

        platillos.put("chaufa", platillo(criollos, "Arroz Chaufa de Pollo", "Arroz salteado al wok",
                "24.00", Map.of(in.get("pollo"), "0.180", in.get("arrozK"), "0.250",
                        in.get("cebolla"), "0.050", in.get("aceite"), "0.020")));

        platillos.put("huancaina", platillo(entradas, "Papa a la Huancaina", "Papa amarilla con salsa huancaina",
                "14.00", Map.of(in.get("papa"), "0.250", in.get("aji"), "0.030")));

        platillos.put("chicharron", platillo(marinos, "Chicharron de Pescado", "Trozos de pescado apanado",
                "30.00", Map.of(in.get("pescado"), "0.250", in.get("aceite"), "0.050",
                        in.get("limon"), "0.050")));

        return platillos;
    }

    private CategoriaPlatillo categoria(String nombre, String descripcion) {
        return categoriaRepository.findByNombreIgnoreCase(nombre)
                .orElseGet(() -> categoriaRepository.saveAndFlush(CategoriaPlatillo.builder()
                        .nombre(nombre).descripcion(descripcion).createdBy("SEED_DEMO").build()));
    }

    private Platillo platillo(CategoriaPlatillo categoria, String nombre, String descripcion,
            String precio, Map<Insumo, String> receta) {
        Platillo platillo = Platillo.builder()
                .categoria(categoria)
                .nombre(nombre)
                .descripcion(descripcion)
                .precioVentaBase(new BigDecimal(precio))
                .activo(true)
                .createdBy("SEED_DEMO")
                .build();

        receta.forEach((insumo, cantidad) -> platillo.getReceta().add(InsumoPlatillo.builder()
                .platillo(platillo)
                .insumo(insumo)
                .cantidadRequerida(new BigDecimal(cantidad))
                .createdBy("SEED_DEMO")
                .build()));

        return platilloRepository.saveAndFlush(platillo);
    }

    private void sembrarComplementosYPromociones(Map<String, Insumo> in) {
        complemento("Inca Kola 500 ml", TipoComplementoEnum.BEBIDA, "6.00", in.get("gaseosa"));
        complemento("Cerveza Pilsen 620 ml", TipoComplementoEnum.CERVEZA, "10.00", in.get("cerveza"));
        complemento("Helado de lucuma", TipoComplementoEnum.HELADO, "8.00", in.get("helado"));
        complemento("Porcion extra de arroz", TipoComplementoEnum.OTROS, "5.00", in.get("arrozK"));
        complemento("Salsa criolla", TipoComplementoEnum.SALSAS, "2.00", null);

        promocionRepository.saveAndFlush(Promocion.builder()
                .nombre("Menu del dia con gaseosa")
                .descripcion("10% de descuento e Inca Kola de regalo")
                .porcentajeDescuento(new BigDecimal("10.00"))
                .montoDescuento(BigDecimal.ZERO)
                .requiereInsumoExtra(true)
                .insumoExtra(in.get("gaseosa"))
                .fechaInicio(ZonedDateTime.now().minusDays(7))
                .fechaFin(ZonedDateTime.now().plusMonths(3))
                .activa(true)
                .createdBy("SEED_DEMO")
                .build());

        promocionRepository.saveAndFlush(Promocion.builder()
                .nombre("Almuerzo de semana")
                .descripcion("5 soles de descuento de lunes a viernes")
                .porcentajeDescuento(BigDecimal.ZERO)
                .montoDescuento(new BigDecimal("5.00"))
                .requiereInsumoExtra(false)
                .fechaInicio(ZonedDateTime.now().minusDays(30))
                .fechaFin(ZonedDateTime.now().plusMonths(6))
                .activa(true)
                .createdBy("SEED_DEMO")
                .build());
    }

    private void complemento(String nombre, TipoComplementoEnum tipo, String precio, Insumo insumo) {
        complementoRepository.saveAndFlush(ComplementoPlatillo.builder()
                .nombre(nombre)
                .tipoComplemento(tipo)
                .precioAdicional(new BigDecimal(precio))
                .insumoAsociado(insumo)
                .activo(true)
                .createdBy("SEED_DEMO")
                .build());
    }

    // -----------------------------------------------------------------
    // Salon, clientes y reparto
    // -----------------------------------------------------------------

    private List<Mesa> sembrarMesas() {
        java.util.List<Mesa> mesas = new java.util.ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            mesas.add(mesa("M" + i, "Salon principal", i <= 4 ? 4 : 6, EstadoMesaEnum.LIBRE));
        }
        for (int i = 1; i <= 3; i++) {
            mesas.add(mesa("T" + i, "Terraza", 2, EstadoMesaEnum.LIBRE));
        }
        // Una reservada, para que el mapa del salon muestre los tres estados.
        Mesa reservada = mesa("T4", "Terraza", 6, EstadoMesaEnum.RESERVADA);
        reservada.setReservadaANombreDe("Familia Zevallos");
        reservada.setReservadaPara(ZonedDateTime.now().plusHours(3));
        mesaRepository.saveAndFlush(reservada);
        mesas.add(reservada);
        return mesas;
    }

    private Mesa mesa(String numero, String zona, int capacidad, EstadoMesaEnum estado) {
        return mesaRepository.saveAndFlush(Mesa.builder()
                .numero(numero).zona(zona).capacidad(capacidad)
                .estado(estado).activa(true).createdBy("SEED_DEMO").build());
    }

    private List<Cliente> sembrarClientes() {
        Cliente rosa = cliente("41258963", "Rosa", "Quispe Ttito", "rosa.quispe@correo.pe",
                "51987654321", "Av. Ejercito 512, Yanahuara", "CF", 4);
        Cliente carlos = cliente("09876543", "Carlos", "Mendoza Rivas", "carlos.mendoza@correo.pe",
                "51956781234", "Calle Mercaderes 210, Cercado", "CD 10%", 1);
        Cliente anonimo = cliente("ANON-DEMO0001", "Cliente", "Sin identificar", null,
                "51900112233", null, null, 0);

        Empresa empresa = empresaRepository.saveAndFlush(Empresa.builder()
                .ruc("20601234567")
                .razonSocial("Inversiones El Chaco S.A.C.")
                .celular("51954001122")
                .direccionFiscal("Av. Parra 145, Arequipa")
                .createdBy("SEED_DEMO")
                .build());

        clienteEmpresaRepository.saveAndFlush(ClienteEmpresa.builder()
                .persona(carlos)
                .empresa(empresa)
                .cargoEnEmpresa("Gerente de operaciones")
                .createdBy("SEED_DEMO")
                .build());

        return List.of(rosa, carlos, anonimo);
    }

    private Cliente cliente(String dni, String nombres, String apellidos, String correo,
            String celular, String direccion, String tipo, int puntos) {
        return clienteRepository.saveAndFlush(Cliente.builder()
                .dni(dni).nombres(nombres).apellidos(apellidos)
                .correo(correo).celular(celular)
                .direccionHabitual(direccion)
                .tipoCliente(tipo)
                .puntosFidelidad(puntos)
                .scoreFraude(0)
                .bloqueadoPorFraude(false)
                .createdBy("SEED_DEMO")
                .build());
    }

    private void sembrarDelivery() {
        Transportista luis = transportistaRepository.saveAndFlush(Transportista.builder()
                .dni("48123456")
                .nombres("Luis")
                .apellidos("Ramos Cardenas")
                .celular("51965432100")
                .empresaTransporte("Motos Express Arequipa")
                .activo(true)
                .createdBy("SEED_DEMO")
                .build());

        vehiculoRepository.saveAndFlush(Vehiculo.builder()
                .transportista(luis)
                .tipoVehiculo(TipoVehiculoEnum.MOTO)
                .placa("V1V-238")
                .marcaModelo("Honda CB 125")
                .activo(true)
                .createdBy("SEED_DEMO")
                .build());
    }

    // -----------------------------------------------------------------
    // Comandas de ejemplo
    // -----------------------------------------------------------------

    private void sembrarComandas(Map<String, Platillo> platillos, List<Mesa> mesas,
            List<Cliente> clientes) {

        // 1. Recien tomada: aparece en la cola del KDS esperando que cocina la tome.
        ordenService.crear(CrearOrdenRequestDto.builder()
                .clienteId(clientes.get(0).getId())
                .tipoOrden(TipoOrdenEnum.MESA)
                .canalOrigen(CanalOrigenEnum.POS)
                .mesaId(mesas.get(0).getId())
                .tipoPago(TipoPagoEnum.EFECTIVO)
                .items(List.of(
                        item(platillos.get("lomo").getId(), 2, "sin cebolla"),
                        item(platillos.get("huancaina").getId(), 1, null)))
                .build());

        // 2. En preparacion: cocina ya la tomo y el cronometro corre.
        OrdenResponseDto enCocina = ordenService.crear(CrearOrdenRequestDto.builder()
                .clienteId(clientes.get(1).getId())
                .tipoOrden(TipoOrdenEnum.MESA)
                .canalOrigen(CanalOrigenEnum.POS)
                .mesaId(mesas.get(2).getId())
                .tipoPago(TipoPagoEnum.TARJETA)
                .items(List.of(
                        item(platillos.get("ceviche").getId(), 1, "bien picante"),
                        item(platillos.get("chicharron").getId(), 1, null)))
                .build());
        ordenService.cambiarEstado(enCocina.getId(),
                CambioEstadoRequestDto.builder().estado(EstadoOrdenEnum.EN_PREPARACION).build());

        // 3. Ciclo completo: entregada, cobrada, concluida y calificada, para que
        //    el arqueo de caja y los reportes de ventas no salgan en cero.
        OrdenResponseDto cerrada = ordenService.crear(CrearOrdenRequestDto.builder()
                .clienteId(clientes.get(0).getId())
                .tipoOrden(TipoOrdenEnum.RETIRO_LOCAL)
                .canalOrigen(CanalOrigenEnum.DISCORD_BOT)
                .tipoPago(TipoPagoEnum.EFECTIVO)
                .items(List.of(item(platillos.get("chaufa").getId(), 2, null)))
                .build());

        ordenService.cambiarEstado(cerrada.getId(),
                CambioEstadoRequestDto.builder().estado(EstadoOrdenEnum.EN_PREPARACION).build());
        ordenService.cambiarEstado(cerrada.getId(),
                CambioEstadoRequestDto.builder().estado(EstadoOrdenEnum.ENTREGADO).build());

        pagoService.registrar(cerrada.getId(), RegistrarPagoRequestDto.builder()
                .tipoPago(TipoPagoEnum.EFECTIVO)
                .monto(cerrada.getMontoTotal())
                .montoEntregado(new BigDecimal("50.00"))
                .build());

        ordenService.cambiarEstado(cerrada.getId(),
                CambioEstadoRequestDto.builder().estado(EstadoOrdenEnum.CONCLUIDO).build());

        fidelizacionService.registrarFeedback(cerrada.getId(), FeedbackRequestDto.builder()
                .puntajeAtencion(5).puntajeComida(5).puntajeLugar(4)
                .comentario("El chaufa estuvo en su punto, llego rapido.")
                .build());
    }

    private ItemOrdenRequestDto item(UUID platilloId, int cantidad, String nota) {
        return ItemOrdenRequestDto.builder()
                .platilloId(platilloId)
                .cantidad(cantidad)
                .excepcionesNota(nota)
                .build();
    }
}
