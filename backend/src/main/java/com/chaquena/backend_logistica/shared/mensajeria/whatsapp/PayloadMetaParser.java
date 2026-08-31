package com.chaquena.backend_logistica.shared.mensajeria.whatsapp;

import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import com.chaquena.backend_logistica.shared.mensajeria.MensajeBot;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Traduce el webhook de la Cloud API de Meta al {@link MensajeBot} neutro que
 * entienden las maquinas de estado.
 *
 * <p>El payload de Meta es un arbol profundo y opcional en casi todos sus
 * niveles, y ademas mezcla mensajes de verdad con acuses de entrega
 * (sent/delivered/read) que no traen ningun mensaje dentro. Toda esa navegacion
 * defensiva vive aqui: es el precio de entrada de este proveedor y no tiene por
 * que contaminar al resto.
 */
@Slf4j
public final class PayloadMetaParser {

    private PayloadMetaParser() {
    }

    /** Devuelve vacio cuando el payload no trae un mensaje que atender. */
    @SuppressWarnings("unchecked")
    public static Optional<MensajeBot> parsear(CanalBot canal, Map<String, Object> payload) {
        try {
            Map<String, Object> value = primero((List<Map<String, Object>>) payload.get("entry"))
                    .flatMap(entry -> primero((List<Map<String, Object>>) entry.get("changes")))
                    .map(cambio -> (Map<String, Object>) cambio.get("value"))
                    .orElse(null);

            if (value == null) {
                return Optional.empty();
            }

            // Acuse de estado de Meta: no hay nada que responder.
            if (value.containsKey("statuses") && !value.containsKey("messages")) {
                return Optional.empty();
            }

            Map<String, Object> mensaje = primero((List<Map<String, Object>>) value.get("messages")).orElse(null);
            if (mensaje == null) {
                return Optional.empty();
            }

            String telefono = (String) mensaje.get("from");
            String messageId = (String) mensaje.get("id");
            String contenido = contenidoDe(mensaje);

            if (telefono == null || contenido == null) {
                return Optional.empty();
            }

            return Optional.of(new MensajeBot(canal, telefono, nombreDe(value), messageId, contenido));
        } catch (Exception e) {
            log.error("⚠️ Payload de WhatsApp con una forma inesperada: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static String contenidoDe(Map<String, Object> mensaje) {
        String tipo = (String) mensaje.get("type");

        if ("text".equals(tipo)) {
            Map<String, Object> texto = (Map<String, Object>) mensaje.get("text");
            return texto == null ? null : (String) texto.get("body");
        }

        if ("interactive".equals(tipo)) {
            Map<String, Object> interactivo = (Map<String, Object>) mensaje.get("interactive");
            if (interactivo == null) {
                return null;
            }
            Map<String, Object> respuesta = (Map<String, Object>) interactivo.get("list_reply");
            if (respuesta == null) {
                respuesta = (Map<String, Object>) interactivo.get("button_reply");
            }
            return respuesta == null ? null : (String) respuesta.get("id");
        }

        // Audios, imagenes, ubicaciones y demas: el bot no los entiende, pero
        // devolver algo permite responderle en lugar de ignorarlo.
        log.info("ℹ️ Mensaje de WhatsApp de tipo '{}' no soportado por los bots.", tipo);
        return "";
    }

    @SuppressWarnings("unchecked")
    private static String nombreDe(Map<String, Object> value) {
        return primero((List<Map<String, Object>>) value.get("contacts"))
                .map(contacto -> (Map<String, Object>) contacto.get("profile"))
                .map(perfil -> (String) perfil.get("name"))
                .orElse(null);
    }

    private static <T> Optional<T> primero(List<T> lista) {
        return lista == null || lista.isEmpty()
                ? Optional.empty()
                : Optional.ofNullable(lista.get(0));
    }
}
