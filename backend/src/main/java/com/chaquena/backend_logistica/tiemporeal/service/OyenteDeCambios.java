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
 * Oyentes post-commit de Hibernate: una venta deshecha no avisa a nadie. Se escucha a Hibernate y no a
 * cada servicio porque mas de veinte caminos cambian una comanda, una mesa o un insumo, y ninguno se olvida.
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
