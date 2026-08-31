package com.chaquena.backend_logistica.inventario.bot.whatsapp;

import com.chaquena.backend_logistica.inventario.bot.service.StockBotService;
import com.chaquena.backend_logistica.shared.mensajeria.CanalBot;
import com.chaquena.backend_logistica.shared.mensajeria.whatsapp.PayloadMetaParser;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Webhook de Meta para el bot interno.
 *
 * <p>Solo existe cuando WhatsApp es el proveedor en servicio. Con Discord no
 * hace falta ningun endpoint publico: el backend abre el WebSocket hacia la
 * pasarela y recibe por ahi, lo que ademas quita de en medio el tunel de
 * cloudflared que este webhook obligaba a levantar en cada demostracion.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/whatsapp/in")
@ConditionalOnProperty(name = "app.mensajeria.proveedor", havingValue = "whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppStockWebhookController {

    private final StockBotService stockBotService;

    @Value("${whatsapp.bot-in.verify-token:}")
    private String verifyToken;

    /** Handshake de alta del webhook. Meta lo llama una vez al configurarlo. */
    @GetMapping(value = "/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verificar(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && !verifyToken.isBlank() && verifyToken.equals(token)) {
            log.info("✅ Handshake de Meta correcto (Bot IN).");
            return ResponseEntity.ok(challenge);
        }
        log.warn("❌ Token de verificacion invalido (Bot IN).");
        return ResponseEntity.status(403).body("Token invalido");
    }

    /**
     * Meta reintenta si no recibe un 200 rapido, asi que se responde siempre
     * afirmativamente y el trabajo se hace antes de contestar. Un error dentro
     * del bot no debe provocar que Meta reenvie el mismo mensaje en bucle.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> recibir(@RequestBody Map<String, Object> payload) {
        PayloadMetaParser.parsear(CanalBot.IN, payload).ifPresent(mensaje -> {
            try {
                stockBotService.procesar(mensaje);
            } catch (Exception e) {
                log.error("Error atendiendo el mensaje de {}: {}", mensaje.remitenteId(), e.getMessage(), e);
            }
        });
        return ResponseEntity.ok().build();
    }
}
