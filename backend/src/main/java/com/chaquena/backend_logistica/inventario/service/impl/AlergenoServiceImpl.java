package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.inventario.domain.Alergeno;
import com.chaquena.backend_logistica.inventario.dto.AlergenoDto;
import com.chaquena.backend_logistica.inventario.repository.AlergenoRepository;
import com.chaquena.backend_logistica.inventario.service.AlergenoService;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlergenoServiceImpl implements AlergenoService {

    private final AlergenoRepository alergenoRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AlergenoDto> listar(boolean soloActivos) {
        List<Alergeno> lista = soloActivos
                ? alergenoRepository.findByActivoTrueOrderByNombreAsc()
                : alergenoRepository.findAllByOrderByNombreAsc();
        return lista.stream().map(AlergenoDto::fromEntity).toList();
    }

    @Override
    @Transactional
    public AlergenoDto crear(AlergenoDto request) {
        String nombre = request.getNombre().trim();
        exigirNombreLibre(nombre, null);

        Alergeno alergeno = Alergeno.builder()
                .nombre(nombre)
                .activo(true)
                .createdBy(UsuarioActual.username())
                .build();
        return AlergenoDto.fromEntity(alergenoRepository.save(alergeno));
    }

    /** Renombrarlo cambia lo que dicen todos los platillos que lo llevan: es el mismo alergeno. */
    @Override
    @Transactional
    public AlergenoDto actualizar(Integer id, AlergenoDto request) {
        Alergeno alergeno = buscar(id);
        String nombre = request.getNombre().trim();
        exigirNombreLibre(nombre, id);

        alergeno.setNombre(nombre);
        alergeno.setModifiedBy(UsuarioActual.username());
        return AlergenoDto.fromEntity(alergenoRepository.save(alergeno));
    }

    /** Darlo de baja no lo quita de los platillos: solo deja de ofrecerse al marcarlos. */
    @Override
    @Transactional
    public AlergenoDto cambiarActivo(Integer id, boolean activo) {
        Alergeno alergeno = buscar(id);
        alergeno.setActivo(activo);
        alergeno.setModifiedBy(UsuarioActual.username());
        return AlergenoDto.fromEntity(alergenoRepository.save(alergeno));
    }

    private void exigirNombreLibre(String nombre, Integer propio) {
        alergenoRepository.findByNombreIgnoreCase(nombre)
                .filter(a -> !a.getId().equals(propio))
                .ifPresent(a -> {
                    throw new ConflictoException("Ya existe el alergeno " + a.getNombre() + ".");
                });
    }

    private Alergeno buscar(Integer id) {
        return alergenoRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el alergeno", id));
    }
}
