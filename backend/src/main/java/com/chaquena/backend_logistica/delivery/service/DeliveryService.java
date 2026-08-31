package com.chaquena.backend_logistica.delivery.service;

import com.chaquena.backend_logistica.delivery.dto.*;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DeliveryService {

    TransportistaResponseDto crearTransportista(TransportistaRequestDto request);
    PageResponseDto<TransportistaResponseDto> listarTransportistas(String empresa, Pageable pageable);
    List<TransportistaResponseDto> transportistasActivos();
    TransportistaResponseDto actualizarTransportista(UUID id, TransportistaRequestDto request);
    TransportistaResponseDto cambiarActivoTransportista(UUID id, boolean activo);

    List<VehiculoResponseDto> vehiculosDe(UUID transportistaId);
    VehiculoResponseDto registrarVehiculo(UUID transportistaId, VehiculoRequestDto request);

    DeliveryInfoDto asignar(UUID ordenId, AsignarDeliveryRequestDto request);
    DeliveryInfoDto despachar(UUID ordenId);
    String reenviarOtp(UUID ordenId);
    DeliveryInfoDto verificarOtp(UUID ordenId, VerificarOtpRequestDto request);
    List<DeliveryInfoDto> tablero();
}
