package com.chaquena.backend_logistica.auth.service.impl;

import com.chaquena.backend_logistica.auth.domain.Cargo;
import com.chaquena.backend_logistica.auth.domain.CargoRol;
import com.chaquena.backend_logistica.auth.domain.Rol;
import com.chaquena.backend_logistica.auth.dto.CargoResponseDto;
import com.chaquena.backend_logistica.auth.dto.CrearCargoRequestDto;
import com.chaquena.backend_logistica.auth.repository.CargoRepository;
import com.chaquena.backend_logistica.auth.repository.CargoRolRepository;
import com.chaquena.backend_logistica.auth.repository.RolRepository;
import com.chaquena.backend_logistica.auth.service.CargoService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CargoServiceImpl implements CargoService {

    private final CargoRepository cargoRepository;
    private final RolRepository rolRepository;
    private final CargoRolRepository cargoRolRepository;

    @Override
    @Transactional
    public CargoResponseDto crear(CrearCargoRequestDto request) {
        if (cargoRepository.existsByNombre(request.getNombre())) {
            throw new IllegalArgumentException("Ya existe un cargo registrado con el nombre: " + request.getNombre());
        }

        Cargo cargo = Cargo.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .createdBy("ADMIN_SYSTEM")
                .build();

        Cargo cargoGuardado = cargoRepository.save(cargo);

        if (request.getRolIds() != null && !request.getRolIds().isEmpty()) {
            for (Integer rolId : request.getRolIds()) {
                Rol rol = rolRepository.findById(rolId)
                        .orElseThrow(() -> new EntityNotFoundException("Rol no encontrado con ID: " + rolId));

                CargoRol cargoRol = CargoRol.builder()
                        .cargo(cargoGuardado)
                        .rol(rol)
                        .createdBy("ADMIN_SYSTEM")
                        .build();

                cargoRolRepository.save(cargoRol);
                cargoGuardado.addCargoRol(cargoRol);
            }
        }

        return CargoResponseDto.fromEntity(cargoGuardado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CargoResponseDto> listarTodos() {
        return cargoRepository.findAll().stream()
                .map(CargoResponseDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CargoResponseDto obtenerPorId(Integer id) {
        Cargo cargo = cargoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cargo no encontrado con ID: " + id));
        return CargoResponseDto.fromEntity(cargo);
    }
}