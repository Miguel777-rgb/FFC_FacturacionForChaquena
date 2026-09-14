package com.chaquena.backend_logistica.inventario.service.impl;

import com.chaquena.backend_logistica.inventario.domain.Proveedor;
import com.chaquena.backend_logistica.inventario.dto.ProveedorDto;
import com.chaquena.backend_logistica.inventario.repository.ProveedorRepository;
import com.chaquena.backend_logistica.inventario.service.ProveedorService;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProveedorServiceImpl implements ProveedorService {

    private final ProveedorRepository proveedorRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProveedorDto> listar(boolean soloActivos) {
        List<Proveedor> lista = soloActivos
                ? proveedorRepository.findByActivoTrueOrderByNombreAsc()
                : proveedorRepository.findAllByOrderByNombreAsc();
        return lista.stream().map(ProveedorDto::fromEntity).toList();
    }

    @Override
    @Transactional
    public ProveedorDto crear(ProveedorDto request) {
        String ruc = limpio(request.getRuc());
        exigirRucLibre(ruc, null);

        Proveedor proveedor = Proveedor.builder()
                .nombre(request.getNombre().trim())
                .ruc(ruc)
                .contacto(limpio(request.getContacto()))
                .telefono(limpio(request.getTelefono()))
                .correo(limpio(request.getCorreo()))
                .activo(true)
                .createdBy(UsuarioActual.username())
                .build();
        return ProveedorDto.fromEntity(proveedorRepository.save(proveedor));
    }

    @Override
    @Transactional
    public ProveedorDto actualizar(UUID id, ProveedorDto request) {
        Proveedor proveedor = buscar(id);
        String ruc = limpio(request.getRuc());
        exigirRucLibre(ruc, id);

        proveedor.setNombre(request.getNombre().trim());
        proveedor.setRuc(ruc);
        proveedor.setContacto(limpio(request.getContacto()));
        proveedor.setTelefono(limpio(request.getTelefono()));
        proveedor.setCorreo(limpio(request.getCorreo()));
        proveedor.setModifiedBy(UsuarioActual.username());
        return ProveedorDto.fromEntity(proveedorRepository.save(proveedor));
    }

    /** Dar de baja no toca sus lotes: siguen diciendo de donde vinieron. */
    @Override
    @Transactional
    public ProveedorDto cambiarActivo(UUID id, boolean activo) {
        Proveedor proveedor = buscar(id);
        proveedor.setActivo(activo);
        proveedor.setModifiedBy(UsuarioActual.username());
        return ProveedorDto.fromEntity(proveedorRepository.save(proveedor));
    }

    private void exigirRucLibre(String ruc, UUID propio) {
        if (ruc == null) return;
        proveedorRepository.findByRuc(ruc)
                .filter(p -> !p.getId().equals(propio))
                .ifPresent(p -> {
                    throw new ConflictoException("El RUC " + ruc + " ya es de " + p.getNombre() + ".");
                });
    }

    private Proveedor buscar(UUID id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el proveedor", id));
    }

    private String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
