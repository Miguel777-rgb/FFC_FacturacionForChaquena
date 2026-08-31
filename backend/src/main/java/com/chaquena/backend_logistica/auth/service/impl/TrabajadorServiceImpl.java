package com.chaquena.backend_logistica.auth.service.impl;

import com.chaquena.backend_logistica.auth.domain.Cargo;
import com.chaquena.backend_logistica.auth.domain.Trabajador;
import com.chaquena.backend_logistica.auth.domain.CargoRol;
import com.chaquena.backend_logistica.auth.domain.Rol;
import com.chaquena.backend_logistica.auth.dto.ActualizarTrabajadorRequestDto;
import com.chaquena.backend_logistica.auth.dto.BootstrapAdminRequestDto;
import com.chaquena.backend_logistica.auth.dto.RegistrarTrabajadorRequestDto;
import com.chaquena.backend_logistica.auth.dto.TrabajadorResponseDto;
import com.chaquena.backend_logistica.auth.repository.RolRepository;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;
import com.chaquena.backend_logistica.auth.repository.CargoRepository;
import com.chaquena.backend_logistica.auth.repository.TrabajadorRepository;
import com.chaquena.backend_logistica.auth.service.TrabajadorService;
import com.chaquena.backend_logistica.personas.repository.PersonaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrabajadorServiceImpl implements TrabajadorService {

    private final TrabajadorRepository trabajadorRepository;
    private final CargoRepository cargoRepository;
    private final PersonaRepository personaRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public TrabajadorResponseDto registrar(RegistrarTrabajadorRequestDto request) {

        // 1. Validar que el Cargo existe
        Cargo cargo = cargoRepository.findById(request.getCargoId())
                .orElseThrow(() -> new EntityNotFoundException("Cargo no encontrado con ID: " + request.getCargoId()));

        // 2. Validaciones de duplicados en Personas y Trabajadores
        if (personaRepository.existsByDni(request.getDni())) {
            throw new IllegalArgumentException("Ya existe una persona registrada con el DNI: " + request.getDni());
        }
        if (request.getCorreo() != null && personaRepository.existsByCorreo(request.getCorreo())) {
            throw new IllegalArgumentException(
                    "Ya existe una persona registrada con el Correo: " + request.getCorreo());
        }
        if (personaRepository.existsByCelular(request.getCelular())) {
            throw new IllegalArgumentException(
                    "Ya existe una persona registrada con el Celular: " + request.getCelular());
        }
        if (trabajadorRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("El username " + request.getUsername() + " ya está en uso.");
        }

        // 3. Crear Entidad Trabajador (Hereda atributos de Persona)
        Trabajador trabajador = Trabajador.builder()
                .dni(request.getDni())
                .nombres(request.getNombres())
                .apellidos(request.getApellidos())
                .correo(request.getCorreo())
                .celular(request.getCelular().replaceAll("[^0-9]", "")) // Guarda sólo dígitos (ej: 51987654321)
                .cargo(cargo)
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .activo(true)
                .createdBy("ADMIN_REGISTRATION")
                .build();

        Trabajador guardado = trabajadorRepository.save(trabajador);
        return TrabajadorResponseDto.fromEntity(guardado);
    }

    /**
     * Alta del primer administrador. Deliberadamente exige que la tabla este
     * vacia: es la unica puerta publica que queda y se cierra sola en cuanto
     * existe un trabajador.
     */
    @Override
    @Transactional
    public TrabajadorResponseDto bootstrapPrimerAdmin(BootstrapAdminRequestDto request) {
        if (trabajadorRepository.count() > 0) {
            throw new ConflictoException(
                    "Ya existen trabajadores registrados. Pide a un administrador que te dé de alta "
                            + "en POST /api/v1/trabajadores.");
        }

        Cargo cargo = cargoRepository.findByNombreIgnoreCase("ADMINISTRADOR")
                .orElseGet(() -> cargoRepository.save(Cargo.builder()
                        .nombre("ADMINISTRADOR")
                        .descripcion("Acceso total al sistema")
                        .createdBy("BOOTSTRAP")
                        .build()));

        Rol rolAdmin = rolRepository.findByNombreIgnoreCase("ADMIN")
                .orElseGet(() -> rolRepository.save(Rol.builder()
                        .nombre("ADMIN")
                        .descripcion("Administrador del local")
                        .createdBy("BOOTSTRAP")
                        .build()));

        boolean yaVinculado = cargo.getCargoRoles() != null && cargo.getCargoRoles().stream()
                .anyMatch(cr -> cr.getRol() != null && "ADMIN".equalsIgnoreCase(cr.getRol().getNombre()));
        if (!yaVinculado) {
            cargo.getCargoRoles().add(CargoRol.builder()
                    .cargo(cargo)
                    .rol(rolAdmin)
                    .createdBy("BOOTSTRAP")
                    .build());
            cargo = cargoRepository.save(cargo);
        }

        Trabajador admin = Trabajador.builder()
                .dni(request.getDni())
                .nombres(request.getNombres())
                .apellidos(request.getApellidos())
                .correo(request.getCorreo())
                .celular(request.getCelular().replaceAll("[^0-9]", ""))
                .cargo(cargo)
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .activo(true)
                .createdBy("BOOTSTRAP")
                .build();

        return TrabajadorResponseDto.fromEntity(trabajadorRepository.save(admin));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<TrabajadorResponseDto> listar(Integer cargoId, Pageable pageable) {
        if (cargoId != null) {
            return PageResponseDto.de(trabajadorRepository.findByCargoId(cargoId, pageable),
                    TrabajadorResponseDto::fromEntity);
        }
        return PageResponseDto.de(trabajadorRepository.findAll(pageable),
                TrabajadorResponseDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrabajadorResponseDto> activos() {
        return trabajadorRepository.findByActivoTrue().stream()
                .map(TrabajadorResponseDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TrabajadorResponseDto obtenerPorId(UUID id) {
        return TrabajadorResponseDto.fromEntity(buscar(id));
    }

    @Override
    @Transactional
    public TrabajadorResponseDto actualizar(UUID id, ActualizarTrabajadorRequestDto request) {
        Trabajador trabajador = buscar(id);
        Cargo cargo = cargoRepository.findById(request.getCargoId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("el cargo", request.getCargoId()));

        trabajador.setNombres(request.getNombres());
        trabajador.setApellidos(request.getApellidos());
        trabajador.setCorreo(request.getCorreo() != null && !request.getCorreo().isBlank()
                ? request.getCorreo() : null);
        if (request.getCelular() != null && !request.getCelular().isBlank()) {
            trabajador.setCelular(request.getCelular().replaceAll("[^0-9]", ""));
        }
        trabajador.setCargo(cargo);
        trabajador.setModifiedBy(UsuarioActual.username());

        return TrabajadorResponseDto.fromEntity(trabajadorRepository.save(trabajador));
    }

    @Override
    @Transactional
    public TrabajadorResponseDto cambiarActivo(UUID id, boolean activo) {
        Trabajador trabajador = buscar(id);
        trabajador.setActivo(activo);
        trabajador.setModifiedBy(UsuarioActual.username());
        return TrabajadorResponseDto.fromEntity(trabajadorRepository.save(trabajador));
    }

    private Trabajador buscar(UUID id) {
        return trabajadorRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el trabajador", id));
    }
}
