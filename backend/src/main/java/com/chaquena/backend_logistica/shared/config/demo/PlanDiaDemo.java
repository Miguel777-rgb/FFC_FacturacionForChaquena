package com.chaquena.backend_logistica.shared.config.demo;

import com.chaquena.backend_logistica.pedidos.domain.CanalOrigenEnum;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.TipoPagoEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * Lo que vende el local en un dia de demostracion: cuantas comandas, a que
 * hora, de que tipo, con que platos, como se pagaron y que dijo el cliente.
 *
 * <p>Es solo calculo, sin base de datos, y es determinista: el mismo dia con el
 * mismo catalogo da siempre las mismas comandas con los mismos ids. Eso es lo
 * que permite rellenar al arrancar sin duplicar nada —lo que ya esta insertado
 * se reconoce por su id— y probar el plan sin levantar Postgres.
 *
 * <p>Las proporciones imitan un local de barrio en Lima: lunes flojo, fines de
 * semana mas fuertes, picos de almuerzo y de cena, la mayoria
 * en mesa, un cuarto a domicilio, y de esos, la mayoria por el bot. Casi todo se
 * cobra; unas pocas se cancelan, y en un par de dias de los sesenta la caja
 * detecta un billete falso.
 */
public final class PlanDiaDemo {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static final BigDecimal CIEN = new BigDecimal("100");

    private PlanDiaDemo() {
    }

    // --- lo que entra -----------------------------------------------------------

    /** Un plato de la carta; `peso` dice cuanto se pide frente a los demas. */
    public record Plato(UUID id, BigDecimal precio, int peso) {
    }

    public record Extra(UUID id, BigDecimal precio) {
    }

    public record MesaDemo(UUID id, String numero, int capacidad) {
    }

    public record Promo(UUID id, BigDecimal porcentaje, BigDecimal monto) {
    }

    /** Un cliente del historial; los de direccion con punto piden delivery ahi. */
    public record ClienteDemo(UUID id, String direccion, Double latitud, Double longitud, int peso) {
        boolean ubicado() {
            return direccion != null && latitud != null && longitud != null;
        }
    }

    public record Reparto(UUID transportistaId, UUID vehiculoId) {
    }

    public record Direccion(String texto, double latitud, double longitud) {
    }

    /**
     * Todo lo que el plan necesita del local. Los mozos van del que mas atiende
     * al que menos; la promocion de almuerzo y la del menu pueden faltar.
     */
    public record Catalogo(
            List<Plato> platos,
            List<Extra> extras,
            List<MesaDemo> mesas,
            Promo almuerzo,
            Promo menu,
            List<UUID> mozos,
            List<ClienteDemo> clientes,
            List<Reparto> repartos,
            UUID cajeroId,
            List<Direccion> direcciones,
            BigDecimal igv) {
    }

    // --- lo que sale ------------------------------------------------------------

    public record LineaPlan(UUID id, UUID platoId, int cantidad, BigDecimal precio,
            UUID extraId, BigDecimal precioExtra, String nota, BigDecimal subtotal) {
    }

    public record PagoPlan(UUID id, TipoPagoEnum tipo, BigDecimal monto, BigDecimal entregado,
            BigDecimal vuelto, String referencia, boolean fraude) {
    }

    public record EncuestaPlan(int atencion, int comida, int lugar, String comentario) {
    }

    /**
     * Una comanda con toda su vida: los cronometros, el pago y la encuesta. `fin`
     * es cuando termino —se cobro, se entrego o se cancelo—; una comanda de hoy
     * solo se siembra si su fin ya paso, para que nada quede a medias en cocina.
     */
    public record ComandaPlan(
            UUID id,
            ZonedDateTime inicio,
            TipoOrdenEnum tipo,
            CanalOrigenEnum canal,
            UUID mozoId,
            UUID clienteId,
            MesaDemo mesa,
            Direccion destino,
            List<LineaPlan> lineas,
            BigDecimal subtotal,
            BigDecimal descuento,
            UUID promocionId,
            BigDecimal total,
            EstadoOrdenEnum estado,
            TipoPagoEnum tipoPago,
            String motivoCancelacion,
            Integer estimadoCocina,
            ZonedDateTime cierreRecepcion,
            ZonedDateTime inicioCocina,
            ZonedDateTime cierrePlatillo,
            ZonedDateTime cierreDespacho,
            ZonedDateTime fin,
            Reparto reparto,
            ZonedDateTime horaDespacho,
            Integer estimadoReparto,
            String otp,
            PagoPlan pago,
            EncuestaPlan encuesta) {
    }

    // --- textos -------------------------------------------------------------------

    private static final List<String> NOTAS = List.of(
            "sin cebolla", "bien cocido", "poco picante", "sin culantro", "para llevar",
            "extra limón", "sin ají", "término medio");

    private static final List<String> MOTIVOS = List.of(
            "El cliente se retiró antes de que saliera el pedido",
            "Pedido duplicado por error",
            "Se acabó el pescado del día",
            "El cliente cambió de opinión",
            "Dirección fuera de la zona de reparto");

    private static final List<String> ELOGIOS = List.of(
            "Muy rico todo, volveremos.",
            "El lomo estaba en su punto.",
            "Atención rápida y amable.",
            "El ceviche, fresquísimo.",
            "Porciones generosas.",
            "Buena sazón, como en casa.",
            "Llegó caliente y a tiempo.");

    private static final List<String> CRITICAS = List.of(
            "Demoró bastante en salir.",
            "El arroz llegó frío.",
            "Mucho ruido en el salón.",
            "Faltó la salsa criolla que pedí.",
            "El delivery tardó más de lo prometido.");

    private static final String ALFABETO_OTP = "0123456789";

    // --- el plan ----------------------------------------------------------------

    /**
     * Las comandas del dia, ordenadas por hora. Se vende todos los dias, tambien
     * el lunes aunque el horario de la demo lo marque cerrado: asi «Hoy» nunca
     * esta en cero en una base de demostracion, sea el dia que sea.
     *
     * @param conFraude si ese dia la caja detecta un billete falso en una cena
     */
    public static List<ComandaPlan> planear(LocalDate dia, Catalogo c, boolean conFraude) {
        if (c.platos().isEmpty()) {
            return List.of();
        }
        Random r = new Random(dia.toEpochDay() * 7919L + 17);

        int cuantas = switch (dia.getDayOfWeek()) {
            case MONDAY -> 22 + r.nextInt(7);
            case TUESDAY, WEDNESDAY, THURSDAY -> 25 + r.nextInt(8);
            case FRIDAY -> 32 + r.nextInt(9);
            default -> 36 + r.nextInt(10);
        };

        List<ZonedDateTime> horas = new ArrayList<>();
        for (int i = 0; i < cuantas; i++) {
            horas.add(dia.atTime(hora(r)).atZone(LIMA));
        }
        horas.sort(Comparator.naturalOrder());

        // La cena con el billete falso: la primera despues de las siete.
        int fraude = -1;
        if (conFraude) {
            for (int i = 0; i < horas.size(); i++) {
                if (horas.get(i).getHour() >= 19) {
                    fraude = i;
                    break;
                }
            }
        }

        List<ComandaPlan> plan = new ArrayList<>();
        for (int i = 0; i < cuantas; i++) {
            plan.add(comanda(dia, i, horas.get(i), c, r, i == fraude));
        }
        return plan;
    }

    /** 60 % almuerzo (12:15–15:30), 30 % cena (19:00–21:45), el resto entre ambos. */
    private static LocalTime hora(Random r) {
        double franja = r.nextDouble();
        // Media de dos uniformes: una campana suave, con el pico al medio de la franja.
        double pico = (r.nextDouble() + r.nextDouble()) / 2;
        int minutos;
        if (franja < 0.6) {
            minutos = 12 * 60 + 15 + (int) (pico * 195);
        } else if (franja < 0.9) {
            minutos = 19 * 60 + (int) (pico * 165);
        } else {
            minutos = 15 * 60 + 30 + r.nextInt(210);
        }
        return LocalTime.of(minutos / 60, minutos % 60, r.nextInt(60));
    }

    private static ComandaPlan comanda(LocalDate dia, int indice, ZonedDateTime inicio, Catalogo c,
            Random r, boolean esFraude) {
        UUID id = uuid("demo-orden:" + dia + ":" + indice);

        double t = r.nextDouble();
        TipoOrdenEnum tipo = t < 0.6 ? TipoOrdenEnum.MESA
                : t < 0.75 ? TipoOrdenEnum.RETIRO_LOCAL : TipoOrdenEnum.DELIVERY;
        CanalOrigenEnum canal = switch (tipo) {
            case DELIVERY -> r.nextDouble() < 0.6 ? CanalOrigenEnum.DISCORD_BOT : CanalOrigenEnum.POS;
            case RETIRO_LOCAL -> r.nextDouble() < 0.3 ? CanalOrigenEnum.DISCORD_BOT : CanalOrigenEnum.POS;
            case MESA -> CanalOrigenEnum.POS;
        };
        // En la fraude no hay bot: el billete se recibe en la caja del local.
        if (esFraude) {
            tipo = TipoOrdenEnum.MESA;
            canal = CanalOrigenEnum.POS;
        }

        // El bot no tiene mozo: la comanda la levanta el propio cliente.
        UUID mozo = canal == CanalOrigenEnum.POS && !c.mozos().isEmpty()
                ? elegirPorPeso(c.mozos(), m -> 5 - Math.min(c.mozos().indexOf(m), 3), r)
                : null;

        double conCliente = switch (tipo) {
            case DELIVERY -> 1.0;
            case RETIRO_LOCAL -> 0.6;
            case MESA -> 0.3;
        };
        ClienteDemo cliente = !c.clientes().isEmpty() && r.nextDouble() < conCliente
                ? elegirPorPeso(tipo == TipoOrdenEnum.DELIVERY ? ubicadosPrimero(c.clientes()) : c.clientes(),
                        ClienteDemo::peso, r)
                : null;

        MesaDemo mesa = tipo == TipoOrdenEnum.MESA && !c.mesas().isEmpty()
                ? c.mesas().get(r.nextInt(c.mesas().size()))
                : null;

        Direccion destino = null;
        if (tipo == TipoOrdenEnum.DELIVERY) {
            if (cliente != null && cliente.ubicado() && r.nextDouble() < 0.7) {
                destino = new Direccion(cliente.direccion(), cliente.latitud(), cliente.longitud());
            } else if (!c.direcciones().isEmpty()) {
                Direccion base = c.direcciones().get(r.nextInt(c.direcciones().size()));
                // Otra puerta de la misma cuadra: unos metros alrededor.
                destino = new Direccion(base.texto(),
                        base.latitud() + (r.nextDouble() - 0.5) * 0.002,
                        base.longitud() + (r.nextDouble() - 0.5) * 0.002);
            }
        }

        // --- lo pedido ---
        List<LineaPlan> lineas = new ArrayList<>();
        int platos = Math.min(tipo == TipoOrdenEnum.MESA ? 1 + r.nextInt(4) : 1 + r.nextInt(3),
                c.platos().size());
        List<Plato> disponibles = new ArrayList<>(c.platos());
        for (int k = 0; k < platos; k++) {
            Plato plato = elegirPorPeso(disponibles, Plato::peso, r);
            disponibles.remove(plato);
            double q = r.nextDouble();
            int cantidad = q < 0.65 ? 1 : q < 0.92 ? 2 : 3;
            Extra extra = !c.extras().isEmpty() && r.nextDouble() < 0.25
                    ? c.extras().get(r.nextInt(c.extras().size()))
                    : null;
            String nota = r.nextDouble() < 0.1 ? NOTAS.get(r.nextInt(NOTAS.size())) : null;
            BigDecimal unitario = plato.precio().add(extra != null ? extra.precio() : BigDecimal.ZERO);
            lineas.add(new LineaPlan(uuid("demo-linea:" + id + ":" + k), plato.id(), cantidad,
                    plato.precio(), extra != null ? extra.id() : null,
                    extra != null ? extra.precio() : null, nota,
                    unitario.multiply(BigDecimal.valueOf(cantidad))));
        }
        BigDecimal subtotal = lineas.stream().map(LineaPlan::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- descuento ---
        BigDecimal descuento = BigDecimal.ZERO;
        UUID promocion = null;
        boolean semana = dia.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue(); // lunes a viernes
        double p = r.nextDouble();
        if (c.almuerzo() != null && semana && inicio.getHour() < 16 && p < 0.12) {
            descuento = c.almuerzo().monto();
            promocion = c.almuerzo().id();
        } else if (c.menu() != null && p > 0.94) {
            descuento = subtotal.multiply(c.menu().porcentaje()).divide(CIEN, 2, RoundingMode.HALF_UP);
            promocion = c.menu().id();
        }
        descuento = descuento.min(subtotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(descuento).setScale(2, RoundingMode.HALF_UP);

        // --- lo que paso ---
        EstadoOrdenEnum estado = esFraude ? EstadoOrdenEnum.FRAUDULENTO
                : r.nextDouble() < 0.03 ? EstadoOrdenEnum.CANCELADO : EstadoOrdenEnum.CONCLUIDO;

        ZonedDateTime cierreRecepcion = inicio.plusSeconds(60 + r.nextInt(120));

        if (estado == EstadoOrdenEnum.CANCELADO) {
            String motivo = tipo == TipoOrdenEnum.DELIVERY
                    ? MOTIVOS.get(r.nextInt(MOTIVOS.size()))
                    : MOTIVOS.get(r.nextInt(MOTIVOS.size() - 1));
            ZonedDateTime fin = inicio.plusMinutes(3 + r.nextInt(10));
            return new ComandaPlan(id, inicio, tipo, canal, mozo, cliente != null ? cliente.id() : null,
                    mesa, destino, lineas, subtotal, descuento, promocion, total, estado,
                    TipoPagoEnum.EFECTIVO, motivo, null, cierreRecepcion, null, null, null, fin,
                    null, null, null, otp(tipo, r), null, null);
        }

        int estimado = platos >= 3 ? 25 : platos == 2 ? 20 : 15;
        ZonedDateTime inicioCocina = inicio.plusMinutes(2 + r.nextInt(4));
        long coccion = Math.max(6, Math.min(45, Math.round(16 + r.nextGaussian() * 6)));
        ZonedDateTime cierrePlatillo = inicioCocina.plusMinutes(coccion);

        ZonedDateTime cierreDespacho;
        ZonedDateTime fin;
        ZonedDateTime horaDespacho = null;
        Integer estimadoReparto = null;
        Reparto reparto = null;
        switch (tipo) {
            case MESA -> {
                cierreDespacho = cierrePlatillo.plusMinutes(1 + r.nextInt(3));
                fin = cierreDespacho.plusMinutes(20 + r.nextInt(36)); // comen y piden la cuenta
            }
            case RETIRO_LOCAL -> {
                cierreDespacho = cierrePlatillo.plusMinutes(2 + r.nextInt(9));
                fin = cierreDespacho;
            }
            default -> {
                reparto = c.repartos().isEmpty() ? null : c.repartos().get(r.nextInt(c.repartos().size()));
                estimadoReparto = 25 + r.nextInt(16);
                horaDespacho = cierrePlatillo.plusMinutes(3 + r.nextInt(8));
                cierreDespacho = horaDespacho.plusMinutes(12 + r.nextInt(24));
                fin = cierreDespacho;
            }
        }

        PagoPlan pago = pago(id, canal, total, r, esFraude);
        EncuestaPlan encuesta = !esFraude && r.nextDouble() < 0.4 ? encuesta(r) : null;

        return new ComandaPlan(id, inicio, tipo, canal, mozo, cliente != null ? cliente.id() : null,
                mesa, destino, lineas, subtotal, descuento, promocion, total, estado, pago.tipo(), null,
                estimado, cierreRecepcion, inicioCocina, cierrePlatillo, cierreDespacho, fin,
                reparto, horaDespacho, estimadoReparto, otp(tipo, r), pago, encuesta);
    }

    /**
     * El bot cobra con billetera o tarjeta; en el local, casi la mitad paga en
     * efectivo y recibe vuelto del billete con el que pago.
     */
    private static PagoPlan pago(UUID orden, CanalOrigenEnum canal, BigDecimal total, Random r,
            boolean fraude) {
        UUID id = uuid("demo-pago:" + orden);
        TipoPagoEnum tipo;
        double t = r.nextDouble();
        if (fraude) {
            tipo = TipoPagoEnum.EFECTIVO;
        } else if (canal == CanalOrigenEnum.DISCORD_BOT) {
            tipo = t < 0.55 ? TipoPagoEnum.E_WALLET : TipoPagoEnum.TARJETA;
        } else {
            tipo = t < 0.45 ? TipoPagoEnum.EFECTIVO : t < 0.8 ? TipoPagoEnum.TARJETA : TipoPagoEnum.E_WALLET;
        }

        BigDecimal entregado = total;
        String referencia = null;
        switch (tipo) {
            case EFECTIVO -> entregado = fraude ? new BigDecimal("100.00").max(total) : billete(total);
            case TARJETA -> referencia = (r.nextBoolean() ? "Visa" : "Mastercard") + " ****"
                    + String.format("%04d", r.nextInt(10_000));
            case E_WALLET -> referencia = (r.nextDouble() < 0.7 ? "Yape" : "Plin") + " · op. "
                    + (10_000_000 + r.nextInt(89_999_999));
        }
        // Con el billete falso la caja no da vuelto: lo detecta antes de devolver nada.
        BigDecimal vuelto = fraude ? BigDecimal.ZERO : entregado.subtract(total);
        return new PagoPlan(id, tipo, total, entregado.setScale(2, RoundingMode.HALF_UP),
                vuelto.setScale(2, RoundingMode.HALF_UP), referencia, fraude);
    }

    /** El billete mas chico que cubre la cuenta, o el siguiente si sobra poco. */
    private static BigDecimal billete(BigDecimal total) {
        for (int valor : new int[] {10, 20, 50, 100, 200}) {
            BigDecimal b = BigDecimal.valueOf(valor);
            if (b.compareTo(total) >= 0) return b;
        }
        return total.setScale(0, RoundingMode.CEILING);
    }

    /** La mayoria contenta; una de cada siete, con algo que decir. */
    private static EncuestaPlan encuesta(Random r) {
        if (r.nextDouble() < 0.15) {
            return new EncuestaPlan(2 + r.nextInt(2), 2 + r.nextInt(3), 3 + r.nextInt(2),
                    CRITICAS.get(r.nextInt(CRITICAS.size())));
        }
        String comentario = r.nextDouble() < 0.45 ? ELOGIOS.get(r.nextInt(ELOGIOS.size())) : null;
        return new EncuestaPlan(4 + r.nextInt(2), 4 + r.nextInt(2), 4 + r.nextInt(2), comentario);
    }

    private static String otp(TipoOrdenEnum tipo, Random r) {
        if (tipo != TipoOrdenEnum.DELIVERY) return null;
        StringBuilder codigo = new StringBuilder();
        for (int i = 0; i < 6; i++) codigo.append(ALFABETO_OTP.charAt(r.nextInt(ALFABETO_OTP.length())));
        return codigo.toString();
    }

    private static List<ClienteDemo> ubicadosPrimero(List<ClienteDemo> clientes) {
        List<ClienteDemo> ubicados = clientes.stream().filter(ClienteDemo::ubicado).toList();
        return ubicados.isEmpty() ? clientes : ubicados;
    }

    private static <T> T elegirPorPeso(List<T> opciones, ToIntFunction<T> peso, Random r) {
        int total = opciones.stream().mapToInt(o -> Math.max(1, peso.applyAsInt(o))).sum();
        int tiro = r.nextInt(total);
        for (T o : opciones) {
            tiro -= Math.max(1, peso.applyAsInt(o));
            if (tiro < 0) return o;
        }
        return opciones.get(opciones.size() - 1);
    }

    /** Un id que depende solo del texto: el mismo dia y la misma posicion dan el mismo id. */
    public static UUID uuid(String semilla) {
        return UUID.nameUUIDFromBytes(semilla.getBytes(StandardCharsets.UTF_8));
    }
}
