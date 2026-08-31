package com.chaquena.backend_logistica.fidelizacion.service.impl;

import com.chaquena.backend_logistica.fidelizacion.domain.ConfiguracionLocal;
import com.chaquena.backend_logistica.fidelizacion.repository.ConfiguracionLocalRepository;
import com.chaquena.backend_logistica.fidelizacion.service.ConfiguracionService;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfiguracionServiceImpl implements ConfiguracionService {

    private final ConfiguracionLocalRepository configuracionRepository;

    @Override
    @Transactional
    public ConfiguracionLocal obtener() {
        return configuracionRepository.findById(ConfiguracionLocal.ID_UNICO)
                .orElseGet(() -> configuracionRepository.save(ConfiguracionLocal.porDefecto()));
    }

    @Override
    @Transactional
    public ConfiguracionLocal actualizar(ConfiguracionLocal cambios) {
        ConfiguracionLocal actual = obtener();
        if (cambios.getCalificacionesParaCupon() != null) {
            actual.setCalificacionesParaCupon(cambios.getCalificacionesParaCupon());
        }
        if (cambios.getPorcentajeDescuentoCupon() != null) {
            actual.setPorcentajeDescuentoCupon(cambios.getPorcentajeDescuentoCupon());
        }
        if (cambios.getDiasVigenciaCupon() != null) {
            actual.setDiasVigenciaCupon(cambios.getDiasVigenciaCupon());
        }
        if (cambios.getMinutosObjetivoCocina() != null) {
            actual.setMinutosObjetivoCocina(cambios.getMinutosObjetivoCocina());
        }
        actual.setModifiedBy(UsuarioActual.username());
        return configuracionRepository.save(actual);
    }

    @Override
    @Transactional
    public int calificacionesParaCupon() {
        Integer valor = obtener().getCalificacionesParaCupon();
        return valor != null ? valor : 5;
    }

    @Override
    @Transactional
    public int minutosObjetivoCocina() {
        Integer valor = obtener().getMinutosObjetivoCocina();
        return valor != null ? valor : 20;
    }
}
