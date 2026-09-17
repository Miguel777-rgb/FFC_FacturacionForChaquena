package com.chaquena.backend_logistica.inventario.controller;

import com.chaquena.backend_logistica.inventario.dto.EstadoLectorCartaDto;
import com.chaquena.backend_logistica.inventario.dto.ImportacionCartaDto;
import com.chaquena.backend_logistica.inventario.dto.LecturaCartaDto;
import com.chaquena.backend_logistica.inventario.dto.ResultadoImportacionCartaDto;
import com.chaquena.backend_logistica.inventario.service.LecturaCartaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Cargar la carta desde fotos. Solo el administrador: una importacion crea o
 * cambia de precio decenas de platillos de una vez.
 *
 * <p>Leer no guarda nada. La pantalla envia las fotos una por una, muestra lo
 * leido para revisarlo y despues importa solo las filas que quedaron marcadas.
 */
@RestController
@RequestMapping("/api/v1/carta")
@RequiredArgsConstructor
@Tag(name = "Catalogo - Lectura de carta", description = "Leer la carta desde fotos e importar lo revisado")
public class LecturaCartaController {

    private final LecturaCartaService lecturaCartaService;

    @GetMapping("/lector")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "estadoLectorCarta", summary = "Si el servidor tiene Tesseract en espanol para leer cartas")
    public ResponseEntity<EstadoLectorCartaDto> estado() {
        return ResponseEntity.ok(lecturaCartaService.estado());
    }

    @PostMapping(path = "/lecturas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "leerCarta",
            summary = "Leer una foto de la carta: secciones, platillos, precios y adicionales, sin guardar",
            description = "JPEG o PNG de hasta 3 MB. Las filas dudosas traen el recorte de la foto de donde salieron.")
    public ResponseEntity<LecturaCartaDto> leer(@RequestPart("imagen") MultipartFile imagen) {
        return ResponseEntity.ok(lecturaCartaService.leer(imagen));
    }

    @PostMapping("/importaciones")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "importarCarta",
            summary = "Crear o actualizar los platillos y complementos revisados",
            description = "Lo que ya existe con el mismo nombre (sin tildes ni mayusculas) se actualiza; lo demas se crea.")
    public ResponseEntity<ResultadoImportacionCartaDto> importar(@Valid @RequestBody ImportacionCartaDto importacion) {
        return ResponseEntity.ok(lecturaCartaService.importar(importacion));
    }
}
