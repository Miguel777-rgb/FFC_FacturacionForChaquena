package com.chaquena.backend_logistica.shared.mensajeria.whatsapp;

import com.chaquena.backend_logistica.shared.mensajeria.BotonBot;
import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import com.chaquena.backend_logistica.shared.mensajeria.DestinoBot;
import com.chaquena.backend_logistica.shared.mensajeria.MensajeriaPort;
import com.chaquena.backend_logistica.shared.mensajeria.OpcionBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador de la Cloud API de Meta. Guarda las credenciales de los dos numeros
 * y exige que cada llamada diga por cual de ellos sale el mensaje, de modo que
 * el numero interno y el de clientes no puedan confundirse por descuido.
 *
 * <p>Queda en el arbol como segundo proveedor: el canal por defecto pasó a ser
 * Discord por el coste por conversacion de WhatsApp, pero mantener vivos los
 * dos adaptadores es justamente lo que demuestra que el puerto sirve. Se activa
 * poniendo {@code app.mensajeria.proveedor=whatsapp}.
 *
 * <p>Los limites de longitud que se aplican aqui (10 filas por lista, 24
 * caracteres de titulo, 3 botones de 20 caracteres) son de Meta: si se
 * sobrepasan, la API rechaza el mensaje entero con un 400 y el cliente se queda
 * esperando una respuesta que nunca llega. Se recortan en silencio en lugar de
 * fallar, porque un titulo truncado es mejor que una conversacion muerta.
 */
@Component
@Slf4j
public class WhatsAppMensajeriaAdapter implements MensajeriaPort {

    private static final int MAX_FILAS_LISTA = 10;
    private static final int MAX_TITULO_FILA = 24;
    private static final int MAX_DESCRIPCION_FILA = 72;
    private static final int MAX_BOTONES = 3;
    private static final int MAX_TITULO_BOTON = 20;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiUrl;
    private final Map<CanalBot, CredencialesWhatsApp> credenciales = new EnumMap<>(CanalBot.class);

    public WhatsAppMensajeriaAdapter(
            @Value("${whatsapp.api.url:https://graph.facebook.com/v19.0}") String apiUrl,
            @Value("${whatsapp.bot-in.phone-number-id:}") String phoneNumberIdIn,
            @Value("${whatsapp.bot-in.token:}") String tokenIn,
            @Value("${whatsapp.bot-out.phone-number-id:}") String phoneNumberIdOut,
            @Value("${whatsapp.bot-out.token:}") String tokenOut) {
        this.apiUrl = apiUrl;
        this.credenciales.put(CanalBot.IN, new CredencialesWhatsApp(phoneNumberIdIn, tokenIn));
        this.credenciales.put(CanalBot.OUT, new CredencialesWhatsApp(phoneNumberIdOut, tokenOut));
    }

    @Override
    public String nombre() {
        return "whatsapp";
    }

    @Override
    public int maxOpciones() {
        return MAX_FILAS_LISTA;
    }

    @Override
    public boolean disponible() {
        return credenciales.values().stream().anyMatch(CredencialesWhatsApp::completas);
    }

    @Override
    public void enviarTexto(CanalBot bot, DestinoBot destino, String mensaje) {
        if (rechazarCanal(bot, destino, "Texto")) {
            return;
        }
        Map<String, Object> body = sobre(destino.id());
        body.put("type", "text");
        body.put("text", Map.of("body", mensaje));
        enviar(bot, body, "Texto");
    }

    @Override
    public void enviarOpciones(CanalBot bot, DestinoBot destino, String tituloHeader, String cuerpo,
            String textoBoton, List<OpcionBot> opciones) {
        if (rechazarCanal(bot, destino, "Lista interactiva")) {
            return;
        }

        List<Map<String, String>> filas = new ArrayList<>();
        for (OpcionBot opcion : opciones.stream().limit(MAX_FILAS_LISTA).toList()) {
            Map<String, String> fila = new HashMap<>();
            fila.put("id", opcion.id());
            fila.put("title", recortar(opcion.titulo(), MAX_TITULO_FILA));
            if (opcion.descripcion() != null && !opcion.descripcion().isBlank()) {
                fila.put("description", recortar(opcion.descripcion(), MAX_DESCRIPCION_FILA));
            }
            filas.add(fila);
        }

        if (opciones.size() > MAX_FILAS_LISTA) {
            log.warn("La lista '{}' traia {} opciones y Meta solo admite {}. Se enviaron las primeras.",
                    tituloHeader, opciones.size(), MAX_FILAS_LISTA);
        }

        Map<String, Object> interactive = new HashMap<>();
        interactive.put("type", "list");
        interactive.put("header", Map.of("type", "text", "text", recortar(tituloHeader, 60)));
        interactive.put("body", Map.of("text", cuerpo));
        interactive.put("action", Map.of(
                "button", recortar(textoBoton, MAX_TITULO_BOTON),
                "sections", List.of(Map.of("title", "Opciones", "rows", filas))));

        Map<String, Object> body = sobre(destino.id());
        body.put("type", "interactive");
        body.put("interactive", interactive);
        enviar(bot, body, "Lista interactiva");
    }

    @Override
    public void enviarBotones(CanalBot bot, DestinoBot destino, String cuerpo, List<BotonBot> botones) {
        if (rechazarCanal(bot, destino, "Botones")) {
            return;
        }

        List<Map<String, Object>> acciones = botones.stream()
                .limit(MAX_BOTONES)
                .map(boton -> Map.<String, Object>of(
                        "type", "reply",
                        "reply", Map.of("id", boton.id(), "title", recortar(boton.etiqueta(), MAX_TITULO_BOTON))))
                .toList();

        Map<String, Object> interactive = new HashMap<>();
        interactive.put("type", "button");
        interactive.put("body", Map.of("text", cuerpo));
        interactive.put("action", Map.of("buttons", acciones));

        Map<String, Object> body = sobre(destino.id());
        body.put("type", "interactive");
        body.put("interactive", interactive);
        enviar(bot, body, "Botones");
    }

    /** Doble check azul sobre el mensaje entrante. */
    @Override
    public void acusarRecibo(CanalBot bot, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("status", "read");
        body.put("message_id", messageId);
        enviar(bot, body, "Marcar como leido (" + messageId + ")");
    }

    // ---------------- Apoyo ----------------

    /**
     * WhatsApp no tiene salas compartidas. Publicar la comanda de cocina en un
     * "canal" aqui significaria mandarsela entera a un telefono que nadie
     * eligio, asi que se rechaza y se dice por que.
     */
    private boolean rechazarCanal(CanalBot bot, DestinoBot destino, String tipoMensaje) {
        if (destino.esCanal()) {
            log.warn("⚠️ [{}] WhatsApp no tiene canales: no se envio [{}] a '{}'. "
                    + "Ese aviso solo funciona con un proveedor con salas compartidas.",
                    bot, tipoMensaje, destino.id());
            return true;
        }
        return false;
    }

    private Map<String, Object> sobre(String telefonoDestino) {
        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", telefonoDestino);
        return body;
    }

    private void enviar(CanalBot bot, Map<String, Object> body, String tipoMensaje) {
        CredencialesWhatsApp cuenta = credenciales.get(bot);

        if (!cuenta.completas()) {
            log.warn("⚠️ El bot {} no tiene phone-number-id o token configurados. "
                    + "No se envio [{}]: revisa WHATSAPP_BOT_{}_PHONE_NUMBER_ID y WHATSAPP_BOT_{}_TOKEN en el .env.",
                    bot, tipoMensaje, bot, bot);
            return;
        }

        try {
            String endpoint = String.format("%s/%s/messages", apiUrl, cuenta.phoneNumberId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(cuenta.token());

            log.info("📤 [{}] Enviando [{}] a {} por {}", bot, tipoMensaje, body.get("to"), endpoint);
            ResponseEntity<String> respuesta = restTemplate.postForEntity(
                    endpoint, new HttpEntity<>(body, headers), String.class);
            log.info("✅ [{}] Meta respondio HTTP {}", bot, respuesta.getStatusCode());
        } catch (Exception e) {
            // Un envio fallido no puede tumbar la conversacion ni la transaccion
            // de negocio que la disparo.
            log.error("❌ [{}] Error enviando [{}] a Meta: {}", bot, tipoMensaje, e.getMessage());
        }
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
