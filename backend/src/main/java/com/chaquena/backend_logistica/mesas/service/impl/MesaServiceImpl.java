package com.chaquena.backend_logistica.mesas.service.impl;

import com.chaquena.backend_logistica.mesas.domain.EstadoMesaEnum;
import com.chaquena.backend_logistica.mesas.domain.Mesa;
import com.chaquena.backend_logistica.mesas.dto.MesaRequestDto;
import com.chaquena.backend_logistica.mesas.dto.MesaResponseDto;
import com.chaquena.backend_logistica.mesas.dto.ReservarMesaRequestDto;
import com.chaquena.backend_logistica.mesas.repository.MesaRepository;
import com.chaquena.backend_logistica.mesas.service.MesaService;
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
public class MesaServiceImpl implements MesaService {

    private final MesaRepository mesaRepository;

    @Override
    @Transactional
    public MesaResponseDto crear(MesaRequestDto request) {
        if (mesaRepository.existsByNumero(request.getNumero())) {
            throw new ConflictoException("Ya existe la mesa " + request.getNumero() + ".");
        }
        Mesa mesa = Mesa.builder()
                .numero(request.getNumero())
                .zona(request.getZona())
                .capacidad(request.getCapacidad())
                .estado(EstadoMesaEnum.LIBRE)
                .activa(request.getActiva() == null || request.getActiva())
                .createdBy(UsuarioActual.username())
                .build();
        return MesaResponseDto.fromEntity(mesaRepository.save(mesa));
    }

    /** Mapa del salon ordenado por zona y numero: es lo que pinta el POS. */
    @Override
    @Transactional(readOnly = true)
    public List<MesaResponseDto> mapaDelSalon() {
        return mesaRepository.findByActivaTrueOrderByZonaAscNumeroAsc().stream()
                .map(MesaResponseDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MesaResponseDto obtenerPorId(UUID id) {
        return MesaResponseDto.fromEntity(buscar(id));
    }

    @Override
    @Transactional
    public MesaResponseDto actualizar(UUID id, MesaRequestDto request) {
        Mesa mesa = buscar(id);
        mesaRepository.findByNumero(request.getNumero())
                .filter(otra -> !otra.getId().equals(id))
                .ifPresent(otra -> {
                    throw new ConflictoException("Ya existe otra mesa con el numero "
                            + request.getNumero() + ".");
                });
        mesa.setNumero(request.getNumero());
        mesa.setZona(request.getZona());
        mesa.setCapacidad(request.getCapacidad());
        if (request.getActiva() != null) {
            mesa.setActiva(request.getActiva());
        }
        mesa.setModifiedBy(UsuarioActual.username());
        return MesaResponseDto.fromEntity(mesaRepository.save(mesa));
    }

    @Override
    @Transactional
    public MesaResponseDto cambiarEstado(UUID id, EstadoMesaEnum estado) {
        Mesa mesa = buscar(id);
        mesa.setEstado(estado);
        if (estado != EstadoMesaEnum.RESERVADA) {
            mesa.setReservadaANombreDe(null);
            mesa.setReservadaPara(null);
        }
        mesa.setModifiedBy(UsuarioActual.username());
        return MesaResponseDto.fromEntity(mesaRepository.save(mesa));
    }

    @Override
    @Transactional
    public MesaResponseDto reservar(UUID id, ReservarMesaRequestDto request) {
        Mesa mesa = buscar(id);
        if (mesa.getEstado() == EstadoMesaEnum.OCUPADA) {
            throw new ConflictoException("La mesa " + mesa.getNumero()
                    + " esta ocupada: no se puede reservar hasta que se libere.");
        }
        mesa.setEstado(EstadoMesaEnum.RESERVADA);
        mesa.setReservadaANombreDe(request.getANombreDe());
        mesa.setReservadaPara(request.getPara());
        mesa.setModifiedBy(UsuarioActual.username());
        return MesaResponseDto.fromEntity(mesaRepository.save(mesa));
    }

    @Override
    @Transactional
    public MesaResponseDto liberar(UUID id) {
        return cambiarEstado(id, EstadoMesaEnum.LIBRE);
    }

    private Mesa buscar(UUID id) {
        return mesaRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la mesa", id));
    }
}
