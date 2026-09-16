package com.chaquena.backend_logistica.reportes.exportacion;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import static java.util.Map.entry;

/**
 * Los textos y formatos de un archivo exportado, en el idioma de quien lo pide.
 *
 * <p>La interfaz existe en espanol, ingles y portugues, y un pdf que se baja
 * desde la pantalla en portugues no puede salir en espanol. El idioma llega en
 * {@code Accept-Language}, que el frontend manda en cada peticion; cualquier
 * otro cae en espanol.
 *
 * <p>Los nombres de los valores de los enums son los mismos del diccionario del
 * frontend, para que la columna "Metodo" diga lo mismo en la pantalla y en el
 * archivo.
 */
public final class Rotulos {

    public static final ZoneId ZONA_DEL_LOCAL = ZoneId.of("America/Lima");

    private enum Idioma {
        ES(Locale.of("es", "PE"), "dd/MM/yyyy", "dd/MM/yyyy HH:mm"),
        EN(Locale.US, "MM/dd/yyyy", "MM/dd/yyyy h:mm a"),
        PT(Locale.of("pt", "BR"), "dd/MM/yyyy", "dd/MM/yyyy HH:mm");

        final Locale locale;
        final DateTimeFormatter fecha;
        final DateTimeFormatter fechaHora;

        Idioma(Locale locale, String fecha, String fechaHora) {
            this.locale = locale;
            this.fecha = DateTimeFormatter.ofPattern(fecha, locale);
            this.fechaHora = DateTimeFormatter.ofPattern(fechaHora, locale);
        }
    }

    /** es, en, pt. */
    private static final Map<String, String[]> TEXTOS = Map.ofEntries(
            entry("rango", t("Del {0} al {1}", "From {0} to {1}", "De {0} a {1}")),
            entry("alMomento", t("Al {0}", "As of {0}", "Em {0}")),
            entry("pagina", t("Página {0}", "Page {0}", "Página {0}")),
            entry("sinDatos", t("Sin datos en este rango.", "No data in this range.", "Sem dados neste período.")),
            entry("total", t("Total", "Total", "Total")),

            entry("ventas.titulo", t("Ventas", "Sales", "Vendas")),
            entry("ventas.resumen", t("Resumen", "Summary", "Resumo")),
            entry("ventas.concepto", t("Concepto", "Item", "Item")),
            entry("ventas.valor", t("Valor", "Value", "Valor")),
            entry("ventas.totalVendido", t("Total vendido", "Total sold", "Total vendido")),
            entry("ventas.comandas", t("Comandas", "Orders", "Comandas")),
            entry("ventas.ticketPromedio", t("Ticket promedio", "Average ticket", "Tíquete médio")),
            entry("ventas.porCanal", t("Por canal", "By channel", "Por canal")),
            entry("ventas.canal", t("Canal", "Channel", "Canal")),
            entry("ventas.porMetodo", t("Por método de pago", "By payment method", "Por forma de pagamento")),
            entry("ventas.metodo", t("Método", "Method", "Forma")),
            entry("ventas.pagos", t("Pagos", "Payments", "Pagamentos")),
            entry("ventas.porDia", t("Por día", "By day", "Por dia")),
            entry("ventas.fecha", t("Fecha", "Date", "Data")),
            entry("ventas.porMozo", t("Por mozo", "By waiter", "Por garçom")),
            entry("ventas.mozo", t("Mozo", "Waiter", "Garçom")),

            entry("productos.titulo", t("Platillos más vendidos", "Best-selling dishes", "Pratos mais vendidos")),
            entry("productos.platillo", t("Platillo", "Dish", "Prato")),
            entry("productos.unidades", t("Unidades", "Units", "Unidades")),
            entry("productos.monto", t("Monto", "Amount", "Valor")),

            entry("inventario.titulo", t("Inventario", "Inventory", "Estoque")),
            entry("inventario.insumo", t("Insumo", "Ingredient", "Insumo")),
            entry("inventario.tipo", t("Tipo", "Type", "Tipo")),
            entry("inventario.unidad", t("Unidad", "Unit", "Unidade")),
            entry("inventario.stock", t("Stock", "Stock", "Estoque")),
            entry("inventario.minimo", t("Mínimo", "Minimum", "Mínimo")),
            entry("inventario.vencimiento", t("Próximo vencimiento", "Next expiry", "Próximo vencimento")),
            entry("inventario.porVencer", t("Por vencer", "Expiring", "A vencer")),
            entry("inventario.vencido", t("Vencido", "Expired", "Vencido")),
            entry("inventario.sinCosto", t("Sin costo", "No cost", "Sem custo")),
            entry("inventario.valor", t("Valor", "Value", "Valor")),

            entry("asistencia.titulo", t("Asistencia", "Attendance", "Presença")),
            entry("asistencia.resumen", t("Resumen por persona", "Summary by person", "Resumo por pessoa")),
            entry("asistencia.detalle", t("Detalle por día", "Detail by day", "Detalhe por dia")),
            entry("asistencia.fecha", t("Fecha", "Date", "Data")),
            entry("asistencia.persona", t("Persona", "Person", "Pessoa")),
            entry("asistencia.cargo", t("Cargo", "Role", "Cargo")),
            entry("asistencia.turno", t("Turno", "Shift", "Turno")),
            entry("asistencia.sinTurno", t("Sin turno", "No shift", "Sem turno")),
            entry("asistencia.entrada", t("Entrada", "Clock-in", "Entrada")),
            entry("asistencia.salida", t("Salida", "Clock-out", "Saída")),
            entry("asistencia.estado", t("Estado", "Status", "Situação")),
            entry("asistencia.minutosTarde", t("Minutos tarde", "Minutes late", "Minutos de atraso")),
            entry("asistencia.turnos", t("Turnos", "Shifts", "Turnos")),
            entry("asistencia.faltas", t("Faltas", "Absences", "Faltas")),
            entry("asistencia.tardanzas", t("Tardanzas", "Late arrivals", "Atrasos")),
            entry("asistencia.horas", t("Horas dentro", "Hours in", "Horas dentro")),

            entry("CanalOrigenEnum.POS", t("POS", "POS", "PDV")),
            entry("CanalOrigenEnum.WHATSAPP_BOT", t("Bot de WhatsApp", "WhatsApp bot", "Bot do WhatsApp")),
            entry("CanalOrigenEnum.DISCORD_BOT", t("Bot de Discord", "Discord bot", "Bot do Discord")),
            entry("CanalOrigenEnum.WEB", t("Web", "Web", "Web")),
            entry("TipoPagoEnum.EFECTIVO", t("Efectivo", "Cash", "Dinheiro")),
            entry("TipoPagoEnum.TARJETA", t("Tarjeta", "Card", "Cartão")),
            entry("TipoPagoEnum.E_WALLET", t("Billetera", "Wallet", "Carteira digital")),
            entry("TipoInsumoEnum.COCIDO", t("Cocido", "Cooked", "Cozido")),
            entry("TipoInsumoEnum.NO_COCIDO", t("No cocido", "Raw", "Cru")),
            entry("EstadoAsistenciaEnum.POR_LLEGAR", t("Por llegar", "Expected", "A caminho")),
            entry("EstadoAsistenciaEnum.DENTRO", t("Dentro", "In", "Dentro")),
            entry("EstadoAsistenciaEnum.SALIO", t("Salió", "Left", "Saiu")),
            entry("EstadoAsistenciaEnum.NO_LLEGA", t("No llega", "Not in yet", "Não chegou")),
            entry("EstadoAsistenciaEnum.FALTO", t("Faltó", "Absent", "Faltou")));

    private final Idioma idioma;

    private Rotulos(Idioma idioma) {
        this.idioma = idioma;
    }

    public static Rotulos para(Locale locale) {
        String lengua = locale != null ? locale.getLanguage() : "";
        return new Rotulos(switch (lengua) {
            case "en" -> Idioma.EN;
            case "pt" -> Idioma.PT;
            default -> Idioma.ES;
        });
    }

    /** El texto de la clave con sus {0}, {1}... reemplazados. Una clave que falta se ve tal cual. */
    public String texto(String clave, Object... argumentos) {
        String[] traducciones = TEXTOS.get(clave);
        String texto = traducciones != null ? traducciones[idioma.ordinal()] : clave;
        for (int i = 0; i < argumentos.length; i++) {
            texto = texto.replace("{" + i + "}", String.valueOf(argumentos[i]));
        }
        return texto;
    }

    public String valor(Enum<?> valor) {
        return valor == null ? "" : texto(valor.getDeclaringClass().getSimpleName() + "." + valor.name());
    }

    public String fecha(LocalDate fecha) {
        return fecha == null ? "" : idioma.fecha.format(fecha);
    }

    public String fechaHora(ZonedDateTime instante) {
        return instante == null ? "" : idioma.fechaHora.format(instante.withZoneSameInstant(ZONA_DEL_LOCAL));
    }

    /** Numero con los separadores del idioma; {@code decimales} es el maximo y el minimo a la vez. */
    public String numero(Number numero, int minimoDecimales, int maximoDecimales) {
        if (numero == null) {
            return "";
        }
        DecimalFormat formato = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(idioma.locale));
        formato.setMinimumFractionDigits(minimoDecimales);
        formato.setMaximumFractionDigits(maximoDecimales);
        return formato.format(numero instanceof BigDecimal b ? b : numero);
    }

    public String soles(Number monto) {
        return monto == null ? "" : "S/ " + numero(monto, 2, 2);
    }

    private static String[] t(String es, String en, String pt) {
        return new String[] {es, en, pt};
    }
}
