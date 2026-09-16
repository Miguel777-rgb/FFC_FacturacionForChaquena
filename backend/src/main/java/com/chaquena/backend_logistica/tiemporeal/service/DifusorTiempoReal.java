package com.chaquena.backend_logistica.tiemporeal.service;

import com.chaquena.backend_logistica.tiemporeal.domain.TemaEnum;
import com.chaquena.backend_logistica.tiemporeal.dto.AvisoTiempoRealDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las conexiones abiertas del stream y el reparto de avisos entre ellas.
 *
 * <p>Los avisos no salen en el momento en que se anotan, sino en rafagas cada
 * pocos cientos de milisegundos. Vender un lomo saltado escribe la comanda, sus
 * lineas, la mesa, cinco insumos y sus lotes: sin juntar, cocina recibiria una
 * docena de avisos iguales y pediria la cola una docena de veces.
 *
 * <p>Vive en memoria, asi que solo sirve con una instancia del backend. Con
 * varias habria que pasar los avisos por Redis, que ya esta en el stack.
 */
@Slf4j
@Component
public class DifusorTiempoReal {

    /**
     * Cuanto dura una conexion antes de que el servidor la cierre. El navegador
     * reconecta al instante, y en cada reconexion manda el token vigente: una
     * sesion cerrada o vencida deja de recibir avisos a los pocos minutos.
     */
    static final Duration VIDA_DE_LA_CONEXION = Duration.ofMinutes(10);

    private final Set<Suscripcion> suscripciones = ConcurrentHashMap.newKeySet();
    private final Set<TemaEnum> pendientes = ConcurrentHashMap.newKeySet();

    record Suscripcion(SseEmitter emisor, Set<String> cargos) {
    }

    /** Abre una conexion para una sesion con estos cargos y le confirma que ya escucha. */
    public SseEmitter suscribir(Collection<String> cargos) {
        SseEmitter emisor = new SseEmitter(VIDA_DE_LA_CONEXION.toMillis());
        Suscripcion suscripcion = registrar(emisor, cargos);
        // Sin este primer evento el cliente no sabria distinguir "conectado y en
        // silencio" de "la peticion sigue colgada en un proxy".
        enviar(suscripcion, SseEmitter.event().name("listo").data("{}"));
        return emisor;
    }

    Suscripcion registrar(SseEmitter emisor, Collection<String> cargos) {
        Suscripcion suscripcion = new Suscripcion(emisor, Set.copyOf(cargos));
        suscripciones.add(suscripcion);
        emisor.onCompletion(() -> suscripciones.remove(suscripcion));
        emisor.onError(error -> suscripciones.remove(suscripcion));
        emisor.onTimeout(() -> {
            suscripciones.remove(suscripcion);
            emisor.complete();
        });
        return suscripcion;
    }

    /** Deja los temas para la proxima rafaga. */
    public void anotar(Collection<TemaEnum> temas) {
        pendientes.addAll(temas);
    }

    @Scheduled(fixedDelay = 400)
    public void difundirPendientes() {
        if (pendientes.isEmpty()) {
            return;
        }
        Set<TemaEnum> temas = EnumSet.noneOf(TemaEnum.class);
        for (Iterator<TemaEnum> it = pendientes.iterator(); it.hasNext();) {
            temas.add(it.next());
            it.remove();
        }

        for (TemaEnum tema : temas) {
            AvisoTiempoRealDto aviso = new AvisoTiempoRealDto(tema);
            for (Suscripcion suscripcion : suscripciones) {
                if (tema.loRecibe(suscripcion.cargos())) {
                    enviar(suscripcion, SseEmitter.event().name("aviso").data(aviso, MediaType.APPLICATION_JSON));
                }
            }
        }
    }

    /**
     * Un comentario cada 25 segundos. Mantiene viva la conexion a traves de los
     * proxies y descubre las que se cortaron sin avisar, como la de un celular
     * que perdio la red.
     */
    @Scheduled(fixedRate = 25_000)
    public void latir() {
        for (Suscripcion suscripcion : suscripciones) {
            enviar(suscripcion, SseEmitter.event().comment("latido"));
        }
    }

    int conexionesAbiertas() {
        return suscripciones.size();
    }

    private void enviar(Suscripcion suscripcion, SseEmitter.SseEventBuilder evento) {
        try {
            suscripcion.emisor().send(evento);
        } catch (IOException | IllegalStateException e) {
            // El otro lado se fue. No se llama a completeWithError: el contenedor
            // ya cierra la peticion asincrona por su cuenta.
            suscripciones.remove(suscripcion);
            log.debug("Conexion de tiempo real cerrada: {}", e.getMessage());
        }
    }
}
