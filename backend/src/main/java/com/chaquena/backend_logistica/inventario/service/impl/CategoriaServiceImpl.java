package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.inventario.domain.CategoriaPlatillo;
import com.chaquena.backend_logistica.inventario.dto.CategoriaRequestDto;
import com.chaquena.backend_logistica.inventario.dto.CategoriaResponseDto;
import com.chaquena.backend_logistica.inventario.repository.CategoriaPlatilloRepository;
import com.chaquena.backend_logistica.inventario.repository.PlatilloRepository;
import com.chaquena.backend_logistica.inventario.service.CategoriaService;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaServiceImpl implements CategoriaService {

    private final CategoriaPlatilloRepository categoriaRepository;
    private final PlatilloRepository platilloRepository;

    @Override
    @Transactional
    public CategoriaResponseDto crear(CategoriaRequestDto request) {
        if (categoriaRepository.existsByNombreIgnoreCase(request.getNombre())) {
            throw new ConflictoException("Ya existe una categoria llamada " + request.getNombre() + ".");
        }
        CategoriaPlatillo categoria = CategoriaPlatillo.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .createdBy(UsuarioActual.username())
                .build();
        return CategoriaResponseDto.fromEntity(categoriaRepository.save(categoria));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaResponseDto> listarTodas() {
        return categoriaRepository.findAll().stream().map(CategoriaResponseDto::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoriaResponseDto obtenerPorId(Integer id) {
        return CategoriaResponseDto.fromEntity(buscar(id));
    }

    @Override
    @Transactional
    public CategoriaResponseDto actualizar(Integer id, CategoriaRequestDto request) {
        CategoriaPlatillo categoria = buscar(id);
        categoriaRepository.findByNombreIgnoreCase(request.getNombre())
                .filter(otra -> !otra.getId().equals(id))
                .ifPresent(otra -> {
                    throw new ConflictoException("Ya existe otra categoria llamada " + request.getNombre() + ".");
                });
        categoria.setNombre(request.getNombre());
        categoria.setDescripcion(request.getDescripcion());
        categoria.setModifiedBy(UsuarioActual.username());
        return CategoriaResponseDto.fromEntity(categoriaRepository.save(categoria));
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        CategoriaPlatillo categoria = buscar(id);
        if (platilloRepository.existsByCategoriaId(id)) {
            throw new ConflictoException(
                    "No se puede eliminar la categoria porque tiene platillos asociados. "
                            + "Reasignalos primero.");
        }
        categoriaRepository.delete(categoria);
    }

    private CategoriaPlatillo buscar(Integer id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la categoria", id));
    }
}
