package com.chaquena.backend_logistica.delivery.service.impl;

import com.chaquena.backend_logistica.delivery.domain.OrdenDeliveryInfo;
import com.chaquena.backend_logistica.delivery.domain.Transportista;
import com.chaquena.backend_logistica.delivery.domain.Vehiculo;
import com.chaquena.backend_logistica.delivery.dto.*;
import com.chaquena.backend_logistica.delivery.repository.OrdenDeliveryInfoRepository;
import com.chaquena.backend_logistica.delivery.repository.TransportistaRepository;
import com.chaquena.backend_logistica.delivery.repository.VehiculoRepository;
import com.chaquena.backend_logistica.delivery.service.DeliveryService;
import com.chaquena.backend_logistica.shared.mensajeria.EmisorBotCliente;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.domain.Orden;
import com.chaquena.backend_logistica.pedidos.domain.TipoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.repository.OrdenRepository;
import com.chaquena.backend_logistica.pedidos.service.MaquinaEstadosOrden;
import com.chaquena.backend_logistica.shared.dto.PageResponseDto;
import com.chaquena.backend_logistica.shared.exception.ConflictoException;
import com.chaquena.backend_logistica.shared.exception.RecursoNoEncontradoException;
import com.chaquena.backend_logistica.shared.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryServiceImpl implements DeliveryService {

    private final TransportistaRepository transportistaRepository;
    private final VehiculoRepository vehiculoRepository;
    private final OrdenDeliveryInfoRepository deliveryInfoRepository;
    private final OrdenRepository ordenRepository;
    private final MaquinaEstadosOrden maquinaEstados;
    private final EmisorBotCliente emisor;

    // ---------------- Transportistas y vehiculos ----------------

    @Override
    @Transactional
    public TransportistaResponseDto crearTransportista(TransportistaRequestDto request) {
        transportistaRepository.findByDni(request.getDni()).ifPresent(existente -> {
            throw new ConflictoException("Ya existe un transportista con el DNI " + request.getDni() + ".");
        });

        Transportista transportista = Transportista.builder()
                .dni(request.getDni())
                .nombres(request.getNombres())
                .apellidos(request.getApellidos())
                .celular(request.getCelular())
                .correo(request.getCorreo() != null && !request.getCorreo().isBlank()
                        ? request.getCorreo() : null)
                .empresaTransporte(request.getEmpresaTransporte())
                .activo(true)
                .createdBy(UsuarioActual.username())
                .build();

        return TransportistaResponseDto.fromEntity(transportistaRepository.save(transportista), false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<TransportistaResponseDto> listarTransportistas(String empresa, Pageable pageable) {
        if (empresa != null && !empresa.isBlank()) {
            return PageResponseDto.de(
                    transportistaRepository.findByEmpresaTransporteContainingIgnoreCase(empresa.trim(), pageable),
                    t -> TransportistaResponseDto.fromEntity(t, false));
        }
        return PageResponseDto.de(transportistaRepository.findAll(pageable),
                t -> TransportistaResponseDto.fromEntity(t, false));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransportistaResponseDto> transportistasActivos() {
        return transportistaRepository.findByActivoTrue().stream()
                .map(t -> TransportistaResponseDto.fromEntity(t, true))
                .toList();
    }

    @Override
    @Transactional
    public TransportistaResponseDto actualizarTransportista(UUID id, TransportistaRequestDto request) {
        Transportista transportista = buscarTransportista(id);
        transportista.setDni(request.getDni());
        transportista.setNombres(request.getNombres());
        transportista.setApellidos(request.getApellidos());
        transportista.setCelular(request.getCelular());
        transportista.setCorreo(request.getCorreo() != null && !request.getCorreo().isBlank()
                ? request.getCorreo() : null);
        transportista.setEmpresaTransporte(request.getEmpresaTransporte());
        transportista.setModifiedBy(UsuarioActual.username());
        return TransportistaResponseDto.fromEntity(transportistaRepository.save(transportista), false);
    }

    @Override
    @Transactional
    public TransportistaResponseDto cambiarActivoTransportista(UUID id, boolean activo) {
        Transportista transportista = buscarTransportista(id);
        transportista.setActivo(activo);
        transportista.setModifiedBy(UsuarioActual.username());
        return TransportistaResponseDto.fromEntity(transportistaRepository.save(transportista), false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehiculoResponseDto> vehiculosDe(UUID transportistaId) {
        buscarTransportista(transportistaId);
        return vehiculoRepository.findByTransportistaId(transportistaId).stream()
                .map(VehiculoResponseDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public VehiculoResponseDto registrarVehiculo(UUID transportistaId, VehiculoRequestDto request) {
        Transportista transportista = buscarTransportista(transportistaId);
        if (vehiculoRepository.existsByPlacaIgnoreCase(request.getPlaca())) {
            throw new ConflictoException("Ya hay un vehiculo registrado con la placa " + request.getPlaca() + ".");
        }

        Vehiculo vehiculo = Vehiculo.builder()
                .transportista(transportista)
                .tipoVehiculo(request.getTipoVehiculo())
                .placa(request.getPlaca().toUpperCase())
                .marcaModelo(request.getMarcaModelo())
                .activo(request.getActivo() == null || request.getActivo())
                .createdBy(UsuarioActual.username())
                .build();

        return VehiculoResponseDto.fromEntity(vehiculoRepository.save(vehiculo));
    }

    // ---------------- Despacho ----------------

    /**
     * Asigna conductor y vehiculo. Se exige que el vehiculo pertenezca al
     * transportista: registrar placa, DNI y empresa antes de dejar salir el
     * pedido es la cuarta capa anti-fraude del diseno.
     */
    @Override
    @Transactional
    public DeliveryInfoDto asignar(UUID ordenId, AsignarDeliveryRequestDto request) {
        Orden orden = buscarOrden(ordenId);
        exigirDelivery(orden);

        Transportista transportista = buscarTransportista(request.getTransportistaId());
        if (Boolean.FALSE.equals(transportista.getActivo())) {
            throw new ConflictoException("El transportista esta dado de baja y no puede recibir pedidos.");
        }

        Vehiculo vehiculo = vehiculoRepository.findById(request.getVehiculoId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("el vehiculo", request.getVehiculoId()));
        if (vehiculo.getTransportista() == null
                || !vehiculo.getTransportista().getId().equals(transportista.getId())) {
            throw new ConflictoException("El vehiculo con placa " + vehiculo.getPlaca()
                    + " no pertenece al transportista asignado.");
        }

        OrdenDeliveryInfo info = deliveryInfoRepository.findByOrdenId(ordenId)
                .orElseGet(() -> OrdenDeliveryInfo.builder()
                        .orden(orden)
                        .createdBy(UsuarioActual.username())
                        .build());

        if (info.getHoraEntrega() != null) {
            throw new ConflictoException("El pedido ya fue entregado; no se puede reasignar.");
        }

        info.setTransportista(transportista);
        info.setVehiculo(vehiculo);
        info.setTiempoEstimadoMinutos(request.getTiempoEstimadoMinutos());
        info.setOtpVerificado(false);
        info.setModifiedBy(UsuarioActual.username());

        return DeliveryInfoDto.fromEntity(deliveryInfoRepository.save(info), vehiculo.getPlaca());
    }

    @Override
    @Transactional
    public DeliveryInfoDto despachar(UUID ordenId) {
        Orden orden = buscarOrden(ordenId);
        exigirDelivery(orden);

        OrdenDeliveryInfo info = deliveryInfoRepository.findByOrdenId(ordenId)
                .orElseThrow(() -> new ConflictoException(
                        "Asigna transportista y vehiculo antes de despachar el pedido."));

        if (info.getTransportista() == null || info.getVehiculo() == null) {
            throw new ConflictoException(
                    "El pedido no tiene conductor o vehiculo asignado; no puede salir a ruta.");
        }

        maquinaEstados.aplicar(orden, EstadoOrdenEnum.EN_DESPACHO);
        ordenRepository.save(orden);

        info.setHoraDespacho(ZonedDateTime.now());
        info.setModifiedBy(UsuarioActual.username());
        OrdenDeliveryInfo guardado = deliveryInfoRepository.save(info);

        notificarCliente(orden, "Tu pedido salio a reparto con "
                + info.getTransportista().getNombres() + " (placa " + info.getVehiculo().getPlaca() + "). "
                + (info.getTiempoEstimadoMinutos() != null
                        ? "Llega en unos " + info.getTiempoEstimadoMinutos() + " minutos. "
                        : "")
                + "Al recibirlo dictale tu codigo de entrega.");

        return DeliveryInfoDto.fromEntity(guardado, info.getVehiculo().getPlaca());
    }

    @Override
    @Transactional(readOnly = true)
    public String reenviarOtp(UUID ordenId) {
        Orden orden = buscarOrden(ordenId);
        exigirDelivery(orden);

        if (orden.getCodigoOtpEntrega() == null) {
            throw new ConflictoException("La comanda no tiene codigo de entrega generado.");
        }

        notificarCliente(orden, "Tu codigo de entrega es " + orden.getCodigoOtpEntrega()
                + ". Dictalo solo cuando tengas el pedido en la mano.");
        return "Codigo reenviado al cliente.";
    }

    /**
     * Unica via para marcar entregado un delivery. El repartidor no puede
     * cerrarlo sin el codigo que le dicta el cliente.
     */
    @Override
    @Transactional
    public DeliveryInfoDto verificarOtp(UUID ordenId, VerificarOtpRequestDto request) {
        Orden orden = buscarOrden(ordenId);
        exigirDelivery(orden);

        OrdenDeliveryInfo info = deliveryInfoRepository.findByOrdenId(ordenId)
                .orElseThrow(() -> new ConflictoException("El pedido no tiene informacion de despacho."));

        if (Boolean.TRUE.equals(info.getOtpVerificado())) {
            throw new ConflictoException("El pedido ya fue verificado y entregado.");
        }
        if (orden.getCodigoOtpEntrega() == null
                || !orden.getCodigoOtpEntrega().equals(request.getCodigo().trim())) {
            throw new ConflictoException("El codigo de entrega no coincide. "
                    + "Pidele al cliente que lo lea de nuevo desde su chat con el bot.");
        }

        maquinaEstados.aplicar(orden, EstadoOrdenEnum.ENTREGADO);
        ordenRepository.save(orden);

        info.setOtpVerificado(true);
        info.setHoraEntrega(ZonedDateTime.now());
        info.setModifiedBy(UsuarioActual.username());
        OrdenDeliveryInfo guardado = deliveryInfoRepository.save(info);

        return DeliveryInfoDto.fromEntity(guardado,
                info.getVehiculo() != null ? info.getVehiculo().getPlaca() : null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryInfoDto> tablero() {
        return deliveryInfoRepository.findByHoraDespachoNotNullAndHoraEntregaIsNull().stream()
                .map(info -> DeliveryInfoDto.fromEntity(info,
                        info.getVehiculo() != null ? info.getVehiculo().getPlaca() : null))
                .toList();
    }

    // ---------------- Apoyo ----------------

    private void notificarCliente(Orden orden, String mensaje) {
        if (orden.getCliente() == null) {
            return;
        }
        String destino = orden.getCliente().identificadorDeBot();
        if (destino == null) {
            // Cliente dado de alta a mano en el POS, sin cuenta de chat.
            return;
        }
        try {
            emisor.enviarTexto(destino, mensaje);
        } catch (Exception e) {
            // Una notificacion fallida no puede tumbar el despacho.
            log.warn("No se pudo notificar al cliente de la comanda {}: {}", orden.getId(), e.getMessage());
        }
    }

    private void exigirDelivery(Orden orden) {
        if (orden.getTipoOrden() != TipoOrdenEnum.DELIVERY) {
            throw new ConflictoException("La comanda no es de delivery: es de tipo " + orden.getTipoOrden() + ".");
        }
    }

    private Transportista buscarTransportista(UUID id) {
        return transportistaRepository.findConVehiculosById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el transportista", id));
    }

    private Orden buscarOrden(UUID id) {
        return ordenRepository.findCompletaById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la comanda", id));
    }
}
