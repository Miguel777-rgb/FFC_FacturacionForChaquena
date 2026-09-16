package com.chaquena.backend_logistica.tiemporeal.service;

import com.chaquena.backend_logistica.tiemporeal.domain.TemaEnum;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCommitDeleteEventListener;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostCommitUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Se entera de cada fila escrita y, si alguna pantalla escucha esa tabla, lo
 * anota para el siguiente aviso.
 *
 * <p>Los tres oyentes son los de "despues de confirmar" de Hibernate: se
 * ejecutan cuando la transaccion ya se escribio, igual que el
 * {@code @TransactionalEventListener(AFTER_COMMIT)} de {@code OrdenCreadaEvent}.
 * Una venta que se deshace por falta de stock no avisa a nadie, y un aviso que
 * falla no puede deshacer la venta.
 *
 * <p>Escuchar a Hibernate, y no publicar un evento desde cada servicio, es lo
 * que evita olvidarse de uno: hay mas de veinte caminos que cambian una
 * comanda, una mesa o un insumo, contando los bots.
 */
@Component
@RequiredArgsConstructor
public class OyenteDeCambios
        implements PostCommitInsertEventListener, PostCommitUpdateEventListener, PostCommitDeleteEventListener {

    private final EntityManagerFactory entityManagerFactory;
    private final DifusorTiempoReal difusor;

    @PostConstruct
    void registrar() {
        EventListenerRegistry registro = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry()
                .requireService(EventListenerRegistry.class);
        registro.appendListeners(EventType.POST_COMMIT_INSERT, this);
        registro.appendListeners(EventType.POST_COMMIT_UPDATE, this);
        registro.appendListeners(EventType.POST_COMMIT_DELETE, this);
    }

    /** Solo las entidades que alguien escucha cargan con el trabajo de despues de confirmar. */
    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return !TemasDeEntidad.de(persister.getMappedClass()).isEmpty();
    }

    @Override
    public void onPostInsert(PostInsertEvent evento) {
        anotar(evento.getPersister());
    }

    @Override
    public void onPostUpdate(PostUpdateEvent evento) {
        anotar(evento.getPersister());
    }

    @Override
    public void onPostDelete(PostDeleteEvent evento) {
        anotar(evento.getPersister());
    }

    @Override
    public void onPostInsertCommitFailed(PostInsertEvent evento) {
        // La transaccion no se confirmo: no hay nada que contar.
    }

    @Override
    public void onPostUpdateCommitFailed(PostUpdateEvent evento) {
    }

    @Override
    public void onPostDeleteCommitFailed(PostDeleteEvent evento) {
    }

    private void anotar(EntityPersister persister) {
        Set<TemaEnum> temas = TemasDeEntidad.de(persister.getMappedClass());
        if (!temas.isEmpty()) {
            difusor.anotar(temas);
        }
    }
}
