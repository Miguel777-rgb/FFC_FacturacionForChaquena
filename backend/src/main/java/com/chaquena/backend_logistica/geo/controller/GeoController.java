package com.chaquena.backend_logistica.geo.controller;

import com.chaquena.backend_logistica.geo.dto.DireccionDto;
import com.chaquena.backend_logistica.geo.dto.EstadoGeoDto;
import com.chaquena.backend_logistica.geo.dto.RutaDto;
import com.chaquena.backend_logistica.geo.service.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * El mapa de direcciones: lo usan quienes escriben una direccion —el mozo en el
 * delivery, la caja en la ficha del cliente y el administrador al marcar el
 * local—. Despacho solo muestra el punto guardado y no pasa por aqui.
 *
 * <p>Todo pasa por el servidor para que la clave de OpenRouteService no llegue
 * al navegador.
 */
@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MOZO','CAJA')")
@Tag(name = "Mapas", description = "Direcciones y distancias sobre el mapa (OpenRouteService)")
public class GeoController {

    private final GeoService geoService;

    @GetMapping("/estado")
    @Operation(operationId = "estadoGeo", summary = "Si hay geocodificador configurado")
    public ResponseEntity<EstadoGeoDto> estado() {
        return ResponseEntity.ok(geoService.estado());
    }

    @GetMapping("/direccion")
    @Operation(operationId = "direccionEnPunto", summary = "La direccion del punto tocado en el mapa")
    public ResponseEntity<DireccionDto> direccion(@RequestParam double latitud, @RequestParam double longitud) {
        return ResponseEntity.ok(geoService.direccionEn(latitud, longitud));
    }

    @GetMapping("/sugerencias")
    @Operation(operationId = "sugerirDirecciones", summary = "Direcciones que empiezan como lo escrito, cerca del local")
    public ResponseEntity<List<DireccionDto>> sugerencias(@RequestParam String texto) {
        return ResponseEntity.ok(geoService.sugerencias(texto));
    }

    @GetMapping("/ruta")
    @Operation(operationId = "rutaDesdeElLocal", summary = "Distancia y tiempo en auto desde el local")
    public ResponseEntity<RutaDto> ruta(@RequestParam double latitud, @RequestParam double longitud) {
        return ResponseEntity.ok(geoService.rutaDesdeElLocal(latitud, longitud));
    }
}
