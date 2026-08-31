package com.chaquena.backend_logistica.reportes.service;

import com.chaquena.backend_logistica.reportes.dto.ProductoTopDto;
import com.chaquena.backend_logistica.reportes.dto.ReporteVentasDto;

import java.time.ZonedDateTime;
import java.util.List;

public interface ReporteService {
    ReporteVentasDto ventas(ZonedDateTime desde, ZonedDateTime hasta);
    List<ProductoTopDto> productosTop(ZonedDateTime desde, ZonedDateTime hasta, int limite);
}
