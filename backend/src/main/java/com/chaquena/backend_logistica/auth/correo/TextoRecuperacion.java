package com.chaquena.backend_logistica.auth.correo;

import java.util.Locale;

/**
 * El texto del correo de recuperacion, en el idioma de quien lo pidio.
 *
 * <p>La interfaz existe en espanol, ingles y portugues, y quien pulso «olvide
 * mi contrasena» en la pantalla en portugues no puede recibir un correo en
 * espanol. El idioma llega en {@code Accept-Language}, la misma cabecera que ya
 * decide el idioma de los PDF exportados; cualquier otro cae en espanol.
 *
 * <p>El cuerpo va en texto plano y no en HTML. Un correo de recuperacion se lee
 * de un vistazo y se pulsa: el HTML solo anadiria peso, riesgo de que el cliente
 * lo recorte y una plantilla mas que mantener. Ademas, el enlace en claro deja
 * ver a donde lleva antes de pulsarlo, que es justo lo que se le pide mirar a
 * alguien antes de escribir una contrasena.
 */
public enum TextoRecuperacion {

    ES("Restablece tu contraseña de Chaquena Logística",
       """
       Hola, %s:

       Alguien pidió restablecer la contraseña de tu cuenta en Chaquena Logística.
       Si fuiste tú, abre este enlace y elige una nueva:

       %s

       El enlace caduca en %d minutos y solo sirve una vez.

       Si no lo pediste, no tienes que hacer nada: tu contraseña sigue siendo la
       de siempre y este enlace dejará de valer solo.
       """),

    EN("Reset your Chaquena Logística password",
       """
       Hello %s,

       Someone asked to reset the password of your Chaquena Logística account.
       If it was you, open this link and choose a new one:

       %s

       The link expires in %d minutes and works only once.

       If you did not ask for it, there is nothing to do: your password stays as
       it is and this link will expire on its own.
       """),

    PT("Redefina sua senha do Chaquena Logística",
       """
       Olá, %s:

       Alguém pediu para redefinir a senha da sua conta no Chaquena Logística.
       Se foi você, abra este link e escolha uma nova:

       %s

       O link expira em %d minutos e serve apenas uma vez.

       Se não foi você, não precisa fazer nada: sua senha continua a mesma e este
       link deixará de valer sozinho.
       """);

    private final String asunto;
    private final String cuerpo;

    TextoRecuperacion(String asunto, String cuerpo) {
        this.asunto = asunto;
        this.cuerpo = cuerpo;
    }

    /** El idioma de la cabecera {@code Accept-Language}; lo desconocido es espanol. */
    public static TextoRecuperacion de(Locale locale) {
        if (locale == null) {
            return ES;
        }
        return switch (locale.getLanguage()) {
            case "en" -> EN;
            case "pt" -> PT;
            default -> ES;
        };
    }

    public String asunto() {
        return asunto;
    }

    public String cuerpo(String nombre, String enlace, long minutos) {
        return cuerpo.formatted(nombre, enlace, minutos);
    }
}
