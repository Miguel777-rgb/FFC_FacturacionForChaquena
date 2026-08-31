package com.chaquena.backend_logistica.pedidos.domain;

/**
 * Por donde entro la comanda.
 *
 * <p>{@code WHATSAPP_BOT} sigue aqui aunque el canal de WhatsApp este apagado:
 * las comandas historicas guardan este valor como texto en
 * {@code ordenes.canal_origen} y quitarlo del enum haria ilegibles las ventas
 * ya cerradas.
 */
public enum CanalOrigenEnum {

    /** Punto de venta web, con el mozo delante de la pantalla. */
    POS,

    /** Bot de WhatsApp. Historico: el canal se retiro por coste por conversacion. */
    WHATSAPP_BOT,

    /** Bot de Discord, tanto el del cliente como el del mozo. */
    DISCORD_BOT,

    /** Pedido desde la web publica. */
    WEB
}