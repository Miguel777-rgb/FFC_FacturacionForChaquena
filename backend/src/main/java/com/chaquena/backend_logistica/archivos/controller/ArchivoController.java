package com.chaquena.backend_logistica.archivos.controller;

import com.chaquena.backend_logistica.archivos.dto.ArchivoDto;
import com.chaquena.backend_logistica.archivos.service.ArchivoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/archivos")
@RequiredArgsConstructor
@Tag(name = "Archivos", description = "Fotos de la carta y logo del local")
public class ArchivoController {

    private final ArchivoService archivoService;

    /** Subir no cuelga la imagen de nada: el id se envia despues con el platillo o con el logo. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','ALMACEN')")
    @Operation(operationId = "subirArchivo", summary = "Subir una imagen WebP, PNG o JPEG de hasta 2 MB")
    public ResponseEntity<ArchivoDto> subir(@RequestPart("archivo") MultipartFile archivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(archivoService.subir(archivo));
    }

    /**
     * Publico: un {@code <img>} no manda la cabecera Authorization. El id es un
     * UUID aleatorio, asi que no se puede recorrer, y lo que hay detras es la
     * foto de un plato o el logo que ya esta en la puerta del local.
     *
     * <p>Un archivo nunca cambia, asi que se cachea doce meses. La politica de
     * contenido vacia es por si alguien abre la URL directamente: no hay nada
     * que ejecutar en una imagen.
     */
    @GetMapping("/{id}")
    @Operation(operationId = "descargarArchivo", summary = "La imagen tal como se subio")
    public ResponseEntity<Resource> descargar(@PathVariable UUID id) {
        ArchivoService.Contenido contenido = archivoService.leer(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contenido.tipoContenido()))
                .contentLength(contenido.tamanoBytes())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("Content-Security-Policy", "default-src 'none'")
                .body(contenido.recurso());
    }
}
