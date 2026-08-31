package com.chaquena.backend_logistica.cocina.service;

import com.chaquena.backend_logistica.cocina.dto.ComandaKdsDto;
import com.chaquena.backend_logistica.cocina.dto.KpisCocinaDto;
import com.chaquena.backend_logistica.cocina.dto.ReportarFaltanteRequestDto;
import com.chaquena.backend_logistica.pedidos.dto.OrdenResponseDto;
import com.chaquena.backend_logistica.shared.dto.MensajeDto;

import java.util.List;
import java.util.UUID;

public interface KdsService {
    List<ComandaKdsDto> cola();
    OrdenResponseDto tomar(UUID ordenId);

    /**
     * Cocina promete un tiempo de preparacion en minutos.
     *
     * <p>Es una promesa, no una medicion: los cronometros de la comanda siguen
     * registrando lo que tardo de verdad. Tener las dos cifras es lo que permite
     * decirle al cliente cuanto falta y, con el tiempo, saber si la cocina se
     * conoce a si misma.
     */
    OrdenResponseDto estimarTiempo(UUID ordenId, int minutos);
    OrdenResponseDto marcarListo(UUID ordenId);
    MensajeDto marcarDetalleListo(UUID detalleId);
    MensajeDto reportarFaltante(UUID ordenId, ReportarFaltanteRequestDto request);
    KpisCocinaDto kpis();
}
