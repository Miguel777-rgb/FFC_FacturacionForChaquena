package com.chaquena.backend_logistica.auth.service.impl;

import com.chaquena.backend_logistica.auth.domain.Cargo;
import com.chaquena.backend_logistica.auth.domain.CargoRol;
import com.chaquena.backend_logistica.auth.domain.Rol;
import com.chaquena.backend_logistica.auth.dto.ActualizarCargoRequestDto;
import com.chaquena.backend_logistica.auth.dto.CargoResponseDto;
import com.chaquena.backend_logistica.auth.dto.CrearCargoRequestDto;
import com.chaquena.backend_logistica.auth.repository.CargoRepository;
import com.chaquena.backend_logistica.auth.repository.RolRepository;
import com.chaquena.backend_logistica.auth.service.CargoService;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CargoServiceImpl implements CargoService {

    private final CargoRepository cargoRepository;
    private final RolRepository rolRepository;

    @Override
    @Transactional
    public CargoResponseDto crear(CrearCargoRequestDto request) {
        if (cargoRepository.existsByNombre(request.getNombre())) {
            throw new ConflictoException("Ya existe un cargo registrado con el nombre: " + request.getNombre());
        }

        Cargo cargo = Cargo.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .createdBy(UsuarioActual.username())
                .build();

        aplicarRoles(cargo, request.getRolIds());

        return CargoResponseDto.fromEntity(cargoRepository.save(cargo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CargoResponseDto> listarTodos() {
        return cargoRepository.findAll(Sort.by("nombre")).stream()
                .map(CargoResponseDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CargoResponseDto obtenerPorId(Integer id) {
        return CargoResponseDto.fromEntity(buscar(id));
    }

    /**
     * Cambia el nombre, la descripcion y el conjunto de roles del cargo.
     *
     * <p>Los roles que ya tenia y siguen pidiendose no se tocan: se borran solo
     * los que sobran y se anaden los que faltan. Reescribir la lista entera
     * funcionaria igual, pero perderia la auditoria de cuando se concedio cada
     * permiso, que es justo el dato que se busca cuando algo sale mal.
     *
     * <p>El efecto no es inmediato para quien ya inicio sesion: los roles viajan
     * dentro del JWT, asi que quien tenga un token vivo conserva los permisos que
     * tenia hasta que vuelva a entrar.
     */
    @Override
    @Transactional
    public CargoResponseDto actualizar(Integer id, ActualizarCargoRequestDto request) {
        Cargo cargo = buscar(id);

        if (!cargo.getNombre().equalsIgnoreCase(request.getNombre())
                && cargoRepository.existsByNombre(request.getNombre())) {
            throw new ConflictoException("Ya existe un cargo registrado con el nombre: " + request.getNombre());
        }

        cargo.setNombre(request.getNombre());
        cargo.setDescripcion(request.getDescripcion());
        cargo.setModifiedBy(UsuarioActual.username());

        aplicarRoles(cargo, request.getRolIds());

        return CargoResponseDto.fromEntity(cargoRepository.save(cargo));
    }

    /**
     * Deja el cargo con exactamente los roles pedidos.
     *
     * <p>Los ids repetidos se descartan: la tabla {@code cargo_roles} no tiene
     * unicidad sobre (cargo, rol), asi que sin esto un envio con el mismo rol dos
     * veces dejaria dos filas identicas y el cargo aparentaria tener un permiso
     * duplicado.
     */
    private void aplicarRoles(Cargo cargo, List<Integer> rolIds) {
        Set<Integer> pedidos = new LinkedHashSet<>(rolIds == null ? List.of() : rolIds);

        cargo.getCargoRoles().removeIf(cr -> !pedidos.contains(cr.getRol().getId()));

        Set<Integer> presentes = cargo.getCargoRoles().stream()
                .map(cr -> cr.getRol().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        for (Integer rolId : pedidos) {
            if (presentes.contains(rolId)) {
                continue;
            }
            Rol rol = rolRepository.findById(rolId)
                    .orElseThrow(() -> RecursoNoEncontradoException.de("el rol", rolId));
            cargo.addCargoRol(CargoRol.builder()
                    .rol(rol)
                    .createdBy(UsuarioActual.username())
                    .build());
        }
    }

    private Cargo buscar(Integer id) {
        return cargoRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el cargo", id));
    }
}
