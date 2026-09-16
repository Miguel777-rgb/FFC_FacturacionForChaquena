package com.chaquena.backend_logistica.reportes.service;

import com.chaquena.backend_logistica.reportes.dto.GranularidadEnum;
import com.chaquena.backend_logistica.reportes.dto.ProductoTopDto;
import com.chaquena.backend_logistica.reportes.dto.ReporteVentasDto;
import com.chaquena.backend_logistica.reportes.dto.SerieVentasDto;
import com.chaquena.backend_logistica.reportes.dto.TableroDto;
import com.chaquena.backend_logistica.reportes.dto.VentasPorMetodoPagoDto;
import com.chaquena.backend_logistica.reportes.dto.VentasPorMozoDto;

import java.time.ZonedDateTime;
import java.util.List;

public interface ReporteService {

    ReporteVentasDto ventas(ZonedDateTime desde, ZonedDateTime hasta);

    List<ProductoTopDto> productosTop(ZonedDateTime desde, ZonedDateTime hasta, int limite);

    TableroDto tablero(ZonedDateTime desde, ZonedDateTime hasta);

    SerieVentasDto serieVentas(ZonedDateTime desde, ZonedDateTime hasta, GranularidadEnum granularidad);

    List<VentasPorMozoDto> ventasPorMozo(ZonedDateTime desde, ZonedDateTime hasta);

    List<VentasPorMetodoPagoDto> ventasPorMetodoPago(ZonedDateTime desde, ZonedDateTime hasta);
}
