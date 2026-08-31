package com.chaquena.backend_logistica.shared.mensajeria.discord;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Piezas de Discord. Solo se activan cuando Discord es el proveedor en
 * servicio: con {@code app.mensajeria.proveedor=whatsapp} no se abre ninguna
 * conexion y la dependencia queda inerte.
 *
 * <p>El contenedor de conexiones nace vacio y sin dependencias a proposito.
 * Quien lo llena es {@link ArranqueDiscord}, ya con el contexto en pie. Hacerlo
 * en el constructor creaba un ciclo irresoluble: el adaptador de salida necesita
 * la conexion, los listeners de entrada necesitan los servicios de negocio, los
 * servicios necesitan el adaptador, y la conexion necesitaba los listeners. Al
 * separar "tener el hueco" de "abrir el socket", el ciclo desaparece y ademas la
 * conexion no se abre hasta que el resto del sistema esta listo para atender lo
 * que llegue por ella.
 */
@Configuration
@ConditionalOnProperty(name = "app.mensajeria.proveedor", havingValue = "discord", matchIfMissing = true)
@EnableConfigurationProperties(DiscordProperties.class)
public class DiscordConfig {

    @Bean(destroyMethod = "close")
    public ClientesDiscord clientesDiscord() {
        return new ClientesDiscord();
    }
}
