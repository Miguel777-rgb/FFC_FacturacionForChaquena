package com.chaquena.backend_logistica.shared.mensajeria;

/**
 * Las dos identidades de bot del negocio. Cada una es una cuenta distinta en el
 * servicio de mensajeria de turno, con su propio token y su propia audiencia.
 *
 * <p>No son intercambiables: el bot interno solo habla con trabajadores y el de
 * clientes solo con clientes. Mezclarlos significa, por ejemplo, mandarle a un
 * comensal el codigo OTP de su pedido desde la cuenta que el personal usa para
 * descontar stock. Por eso la identidad viaja como parametro explicito en cada
 * envio y no como un campo fijo del emisor.
 *
 * <p>Este enum sobrevivio al cambio de WhatsApp a Discord sin tocarse: lo que
 * distingue a los dos bots es a quien le hablan, no por que cable salen.
 */
public enum CanalBot {

    /** Bot IN: cuenta interna del personal. Stock, comandas del mozo y cocina. */
    IN,

    /** Bot OUT: cuenta de cara al cliente. Carta, pedidos y delivery. */
    OUT
}
