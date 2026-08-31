package com.chaquena.backend_logistica.shared.mensajeria.whatsapp;

/**
 * Par de credenciales de un numero de WhatsApp. Se resuelven al arrancar desde
 * las propiedades {@code whatsapp.bot-in.*} y {@code whatsapp.bot-out.*}.
 */
public record CredencialesWhatsApp(String phoneNumberId, String token) {

    /**
     * Un numero sin configurar no es un error de arranque: desde que el canal
     * por defecto es Discord, lo normal es que estas claves esten vacias.
     */
    public boolean completas() {
        return phoneNumberId != null && !phoneNumberId.isBlank()
                && token != null && !token.isBlank();
    }
}
