package com.chaquena.backend_logistica.shared.config;

import com.chaquena.backend_logistica.auth.domain.Cargo;
import com.chaquena.backend_logistica.auth.domain.CargoRol;
import com.chaquena.backend_logistica.auth.domain.Permiso;
import com.chaquena.backend_logistica.auth.domain.Rol;
import com.chaquena.backend_logistica.auth.domain.RolPermiso;
import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.repository.*;
import com.chaquena.backend_logistica.auth.service.TrabajadorContexto;
import com.chaquena.backend_logistica.inventario.domain.CategoriaPlatillo;
import com.chaquena.backend_logistica.inventario.repository.CategoriaPlatilloRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Siembra el vocabulario base la primera vez que arranca el sistema: la
 * seguridad y las categorias de la carta. Sin lo primero las tablas roles,
 * permisos y cargo_roles quedan vacias y las anotaciones @PreAuthorize
 * dejarian fuera a todo el mundo, incluido el administrador; sin lo segundo
 * no hay donde colgar un platillo.
 *
 * Solo escribe si las tablas estan vacias: nunca pisa datos existentes.
 * Se puede apagar con app.seed.enabled=false.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DatosInicialesSeeder {

    /** Rol -> modulos sobre los que puede operar. */
    private static final Map<String, List<String>> ROLES = Map.of(
            "ADMIN", List.of("AUTH", "CATALOGO", "INVENTARIO", "CLIENTES", "COMANDAS",
                    "COCINA", "CAJA", "DELIVERY", "REPORTES", "CONFIGURACION"),
            "MOZO", List.of("CLIENTES", "COMANDAS", "CAJA", "DELIVERY"),
            "COCINA", List.of("COCINA", "INVENTARIO", "CATALOGO"),
            "CAJA", List.of("COMANDAS", "CAJA", "REPORTES"),
            "ALMACEN", List.of("INVENTARIO", "CATALOGO"),
            "DELIVERY", List.of("DELIVERY", "COMANDAS"));

    /** Cargo del organigrama -> roles que agrupa. */
    private static final Map<String, List<String>> CARGOS = Map.of(
            "ADMINISTRADOR", List.of("ADMIN"),
            "MOZO", List.of("MOZO"),
            "JEFE DE COCINA", List.of("COCINA"),
            "CAJERO", List.of("CAJA"),
            "ALMACENERO", List.of("ALMACEN"),
            "REPARTIDOR", List.of("DELIVERY"));

    /**
     * Secciones de la carta del local, en el orden en que aparecen impresas.
     * Es dato real del negocio, no de demostracion: la carta se organiza por
     * tecnica de coccion e insumo principal (Parrillas, Broaster, Cuy) mas que
     * por momento del servicio, y las bebidas van desagregadas porque cada
     * bloque tiene su propia carta de precios.
     *
     * Lista ordenada y no un Map: Map.of no conserva el orden y se queda corto
     * a partir de diez pares.
     */
    private static final List<Map.Entry<String, String>> CATEGORIAS_CARTA = List.of(
            Map.entry("Parrillas", "Cortes a la brasa: parrilla familiar, mixtas, combos y anticuchos"),
            Map.entry("Cordero", "Caldo, thimpo y costillar de cordero"),
            Map.entry("Broaster", "Pollo broaster, piernitas, mostrito y salchibroaster"),
            Map.entry("Alitas", "Alitas acevichadas, BBQ y broaster"),
            Map.entry("Chicharrones", "Chicharrón de cerdo y de pollo, del personal al familiar"),
            Map.entry("Trucha", "Trucha frita y chicharrón de trucha"),
            Map.entry("Cuy", "Cuy chactado, broaster y en chicharrón, entero o medio"),
            Map.entry("Pastas", "Tallarines saltados y tallarín verde con bistec o pollo"),
            Map.entry("Gallina y Pollo", "Caldos de gallina y de pollo, y dieta de pollo"),
            Map.entry("Bistec y Saltados", "Bistec a lo pobre, lomo saltado y saltado de pollo"),
            Map.entry("Menú del Día", "Segundos del día: milanesa, chuletas, timbal y menú junior"),
            Map.entry("Arroz Chaufa", "Chaufa de pollo, res, cerdo, trucha, combinado y aeropuerto"),
            Map.entry("Salchipapas", "Salchipapa clásica, salchirrón, salchiroyal y choripapas"),
            Map.entry("Hamburguesas", "Hamburguesas de res y pollo, royal, hawaiana y choriburger"),
            Map.entry("Vegetariano", "Saltados, chaufa, torreja y hamburguesa de verduras"),
            Map.entry("Guarniciones", "Porciones y acompañamientos: papas, arroz, ensaladas y tequeños"),
            Map.entry("Bebidas de la Casa", "Chicha morada, limonada y naranjada, en jarra de litro y frozen"),
            Map.entry("Café e Infusiones", "Café solo o con leche, coca, muña, manzanilla y té"),
            Map.entry("Jugos", "Jugos de fruta al momento, solos o con leche"),
            Map.entry("Vinos", "Santiago Queirolo Rosé y Borgoña de 750 ml"),
            Map.entry("Cervezas", "Cusqueña trigo, dorada y negra, y Pilsen Callao"),
            Map.entry("Gaseosas y Agua", "Gaseosas de Puno, marcas clásicas y agua San Luis"));

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final RolPermisoRepository rolPermisoRepository;
    private final CargoRepository cargoRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final CategoriaPlatilloRepository categoriaRepository;

    @Bean
    @Order(1) // la seguridad se siembra antes que los datos de demostracion
    public ApplicationRunner sembrarSeguridad() {
        return args -> sembrar();
    }

    @Transactional
    public void sembrar() {
        sembrarSeguridadBase();
        sembrarTrabajadorDelBot();
        sembrarCategoriasDeLaCarta();
    }

    /**
     * Crea las secciones de la carta que falten, respetando su orden impreso.
     *
     * <p>No se guarda contra un contador de filas sino categoria por categoria,
     * por nombre: asi un arranque posterior con una seccion nueva en
     * {@link #CATEGORIAS_CARTA} la agrega sin tocar las que ya estan, y una
     * descripcion editada desde el POS no se pisa en el siguiente reinicio. Los
     * ids los asigna la secuencia de Postgres, no esta lista.
     */
    private void sembrarCategoriasDeLaCarta() {
        int creadas = 0;
        for (Map.Entry<String, String> seccion : CATEGORIAS_CARTA) {
            if (categoriaRepository.findByNombreIgnoreCase(seccion.getKey()).isPresent()) {
                continue;
            }
            categoriaRepository.save(CategoriaPlatillo.builder()
                    .nombre(seccion.getKey())
                    .descripcion(seccion.getValue())
                    .createdBy("SEEDER")
                    .build());
            creadas++;
        }

        if (creadas > 0) {
            log.info("Categorias de la carta sembradas: {} nuevas de {} secciones.",
                    creadas, CATEGORIAS_CARTA.size());
        }
    }

    private void sembrarSeguridadBase() {
        if (rolRepository.count() > 0 && cargoRepository.count() > 0) {
            return;
        }

        log.info("Sembrando roles, permisos y cargos base del sistema...");

        ROLES.forEach((nombreRol, modulos) -> {
            Rol rol = rolRepository.findByNombreIgnoreCase(nombreRol)
                    .orElseGet(() -> rolRepository.save(Rol.builder()
                            .nombre(nombreRol)
                            .descripcion("Rol base del sistema: " + nombreRol)
                            .createdBy("SEEDER")
                            .build()));

            for (String modulo : modulos) {
                for (String accion : List.of("LEER", "ESCRIBIR")) {
                    String nombrePermiso = modulo + "_" + accion;
                    Permiso permiso = permisoRepository.findByNombre(nombrePermiso)
                            .orElseGet(() -> permisoRepository.save(Permiso.builder()
                                    .nombre(nombrePermiso)
                                    .modulo(modulo)
                                    .createdBy("SEEDER")
                                    .build()));

                    rolPermisoRepository.save(RolPermiso.builder()
                            .rol(rol)
                            .permiso(permiso)
                            .createdBy("SEEDER")
                            .build());
                }
            }
        });

        CARGOS.forEach((nombreCargo, roles) -> {
            if (cargoRepository.findByNombreIgnoreCase(nombreCargo).isPresent()) {
                return;
            }
            Cargo cargo = Cargo.builder()
                    .nombre(nombreCargo)
                    .descripcion("Cargo base del sistema")
                    .createdBy("SEEDER")
                    .build();

            for (String nombreRol : roles) {
                rolRepository.findByNombreIgnoreCase(nombreRol).ifPresent(rol ->
                        cargo.getCargoRoles().add(CargoRol.builder()
                                .cargo(cargo)
                                .rol(rol)
                                .createdBy("SEEDER")
                                .build()));
            }
            cargoRepository.save(cargo);
        });

        log.info("Seguridad base sembrada: {} roles, {} permisos, {} cargos.",
                rolRepository.count(), permisoRepository.count(), cargoRepository.count());
    }

    /**
     * Crea el trabajador que representa al bot de clientes.
     *
     * <p>Cada movimiento de inventario exige un autor: la columna
     * {@code controles_insumo.trabajador_id} es NOT NULL a proposito, para que
     * el libro de stock nunca tenga una salida de la que nadie responda. Un
     * pedido que el cliente arma solo con el bot no tiene mozo detras, asi que
     * se le da al canal una identidad propia en lugar de dejar el movimiento
     * huerfano o atribuirselo a una persona que no estuvo ahi.
     *
     * <p>Queda inactivo y con un hash de contrasena inservible: existe para
     * firmar movimientos, nunca para iniciar sesion. Se siembra aparte de la
     * seguridad base porque esta ya puede estar puesta de arranques anteriores.
     */
    private void sembrarTrabajadorDelBot() {
        if (trabajadorRepository.existsByUsername(TrabajadorContexto.USERNAME_BOT_CLIENTES)) {
            return;
        }

        cargoRepository.findByNombreIgnoreCase("MOZO").ifPresentOrElse(cargo -> {
            trabajadorRepository.save(Trabajador.builder()
                    .dni("BOT-CLIENTES")
                    .nombres("Bot")
                    .apellidos("de Clientes")
                    .cargo(cargo)
                    .username(TrabajadorContexto.USERNAME_BOT_CLIENTES)
                    .passwordHash("SIN-ACCESO")
                    .activo(false)
                    .createdBy("SEEDER")
                    .build());
            log.info("Trabajador de sistema '{}' sembrado: firma las comandas del bot de clientes.",
                    TrabajadorContexto.USERNAME_BOT_CLIENTES);
        }, () -> log.warn("No existe el cargo MOZO, no se pudo sembrar el trabajador del bot de clientes."));
    }
}
