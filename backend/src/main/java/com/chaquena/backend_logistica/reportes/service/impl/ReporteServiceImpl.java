package com.chaquena.backend_logistica.reportes.service.impl;

import com.chaquena.backend_logistica.pedidos.domain.CanalOrigenEnum;
import com.chaquena.backend_logistica.pedidos.domain.EstadoOrdenEnum;
import com.chaquena.backend_logistica.pedidos.repository.OrdenDetalleRepository;
import com.chaquena.backend_logistica.pedidos.repository.OrdenRepository;
import com.chaquena.backend_logistica.reportes.dto.ProductoTopDto;
import com.chaquena.backend_logistica.reportes.dto.ReporteVentasDto;
import com.chaquena.backend_logistica.reportes.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReporteServiceImpl implements ReporteService {

    /**
     * Una comanda solo cuenta como venta cuando llego al comensal. Las abiertas
     * todavia se pueden cancelar, asi que ni el total ni el desglose por canal
     * las miran; las canceladas y las fraudulentas quedan fuera por lo mismo.
     */
    private static final List<EstadoOrdenEnum> VENTA_EFECTIVA = List.of(
            EstadoOrdenEnum.ENTREGADO, EstadoOrdenEnum.PAGADO, EstadoOrdenEnum.CONCLUIDO);

    private final OrdenRepository ordenRepository;
    private final OrdenDetalleRepository ordenDetalleRepository;

    @Override
    @Transactional(readOnly = true)
    public ReporteVentasDto ventas(ZonedDateTime desde, ZonedDateTime hasta) {
        BigDecimal total = ordenRepository.totalVendido(VENTA_EFECTIVA, desde, hasta);

        List<ReporteVentasDto.PorCanal> porCanal =
                ordenRepository.ventasPorCanal(desde, hasta, VENTA_EFECTIVA).stream()
                        .map(fila -> ReporteVentasDto.PorCanal.builder()
                                .canal((CanalOrigenEnum) fila[0])
                                .cantidadComandas(((Number) fila[1]).longValue())
                                .total((BigDecimal) fila[2])
                                .build())
                        .toList();

        return ReporteVentasDto.builder()
                .desde(desde)
                .hasta(hasta)
                .totalVendido(total != null ? total : BigDecimal.ZERO)
                .porCanal(porCanal)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoTopDto> productosTop(ZonedDateTime desde, ZonedDateTime hasta, int limite) {
        return ordenDetalleRepository.platillosMasVendidos(desde, hasta, VENTA_EFECTIVA).stream()
                .limit(limite)
                .map(fila -> ProductoTopDto.builder()
                        .platillo((String) fila[0])
                        .unidadesVendidas(((Number) fila[1]).longValue())
                        .montoTotal((BigDecimal) fila[2])
                        .build())
                .toList();
    }
}
