package com.chaquena.backend_logistica.fidelizacion.service.impl;

import com.chaquena.backend_logistica.fidelizacion.domain.NivelLealtad;
import com.chaquena.backend_logistica.fidelizacion.dto.NivelLealtadDto;
import com.chaquena.backend_logistica.fidelizacion.repository.NivelLealtadRepository;
import com.chaquena.backend_logistica.fidelizacion.service.NivelLealtadService;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NivelLealtadServiceImpl implements NivelLealtadService {

    private final NivelLealtadRepository nivelRepository;

    @Override
    @Transactional(readOnly = true)
    public List<NivelLealtadDto> listar() {
        return nivelRepository.findAllByOrderByPuntosMinimosAsc().stream()
                .map(NivelLealtadDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public NivelLealtadDto crear(NivelLealtadDto request) {
        String nombre = request.getNombre().trim();
        exigirLibre(nombre, request.getPuntosMinimos(), null);

        NivelLealtad nivel = NivelLealtad.builder()
                .nombre(nombre)
                .puntosMinimos(request.getPuntosMinimos())
                .porcentajeDescuento(request.getPorcentajeDescuento().setScale(2, RoundingMode.HALF_UP))
                .createdBy(UsuarioActual.username())
                .build();
        return NivelLealtadDto.fromEntity(nivelRepository.save(nivel));
    }

    @Override
    @Transactional
    public NivelLealtadDto actualizar(UUID id, NivelLealtadDto request) {
        NivelLealtad nivel = nivelRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el nivel de lealtad", id));
        String nombre = request.getNombre().trim();
        exigirLibre(nombre, request.getPuntosMinimos(), id);

        nivel.setNombre(nombre);
        nivel.setPuntosMinimos(request.getPuntosMinimos());
        nivel.setPorcentajeDescuento(request.getPorcentajeDescuento().setScale(2, RoundingMode.HALF_UP));
        nivel.setModifiedBy(UsuarioActual.username());
        return NivelLealtadDto.fromEntity(nivelRepository.save(nivel));
    }

    /**
     * Borrar un nivel no deja huecos: las comandas guardan el nombre del nivel
     * que aplicaron, no una referencia, y los clientes no tienen el nivel
     * guardado sino que se les calcula por sus puntos.
     */
    @Override
    @Transactional
    public void eliminar(UUID id) {
        if (!nivelRepository.existsById(id)) {
            throw RecursoNoEncontradoException.de("el nivel de lealtad", id);
        }
        nivelRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NivelLealtad> nivelDe(int puntos) {
        return nivelRepository.findAllByOrderByPuntosMinimosAsc().stream()
                .filter(n -> n.getPuntosMinimos() <= puntos)
                .reduce((anterior, siguiente) -> siguiente);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NivelLealtad> siguienteA(int puntos) {
        return nivelRepository.findAllByOrderByPuntosMinimosAsc().stream()
                .filter(n -> n.getPuntosMinimos() > puntos)
                .findFirst();
    }

    private void exigirLibre(String nombre, Integer puntosMinimos, UUID propio) {
        nivelRepository.findByNombreIgnoreCase(nombre)
                .filter(n -> !n.getId().equals(propio))
                .ifPresent(n -> {
                    throw new ConflictoException("Ya hay un nivel llamado " + n.getNombre() + ".");
                });
        nivelRepository.findByPuntosMinimos(puntosMinimos)
                .filter(n -> !n.getId().equals(propio))
                .ifPresent(n -> {
                    throw new ConflictoException("El nivel " + n.getNombre() + " ya empieza en "
                            + puntosMinimos + " puntos: dos niveles no pueden empezar en el mismo punto.");
                });
    }
}
