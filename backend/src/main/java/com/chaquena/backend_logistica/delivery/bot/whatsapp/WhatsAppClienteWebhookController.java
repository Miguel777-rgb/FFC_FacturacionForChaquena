package com.chaquena.backend_logistica.delivery.bot.whatsapp;

import com.chaquena.backend_logistica.delivery.bot.service.ClienteBotService;
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
 * Webhook de Meta para el bot de clientes. Solo existe cuando WhatsApp es el
 * proveedor en servicio; ver {@code WhatsAppStockWebhookController} para el
 * porque.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/whatsapp/out")
@ConditionalOnProperty(name = "app.mensajeria.proveedor", havingValue = "whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppClienteWebhookController {

    private final ClienteBotService clienteBotService;

    @Value("${whatsapp.bot-out.verify-token:}")
    private String verifyToken;

    @GetMapping(value = "/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verificar(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && !verifyToken.isBlank() && verifyToken.equals(token)) {
            log.info("✅ Handshake de Meta correcto (Bot OUT).");
            return ResponseEntity.ok(challenge);
        }
        log.warn("❌ Token de verificacion invalido (Bot OUT).");
        return ResponseEntity.status(403).body("Token invalido");
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> recibir(@RequestBody Map<String, Object> payload) {
        PayloadMetaParser.parsear(CanalBot.OUT, payload)
                .ifPresent(clienteBotService::procesar);
        return ResponseEntity.ok().build();
    }
}
