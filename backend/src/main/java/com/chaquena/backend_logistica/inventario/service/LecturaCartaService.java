package com.chaquena.backend_logistica.inventario.service;

import com.chaquena.backend_logistica.inventario.dto.EstadoLectorCartaDto;
import com.chaquena.backend_logistica.inventario.dto.ImportacionCartaDto;
import com.chaquena.backend_logistica.inventario.dto.LecturaCartaDto;
import com.chaquena.backend_logistica.inventario.dto.ResultadoImportacionCartaDto;
import org.springframework.web.multipart.MultipartFile;

/**
 * Cargar la carta desde fotos: leer cada foto con Tesseract, sin guardar nada,
 * e importar despues solo lo que se reviso.
 */
public interface LecturaCartaService {

    EstadoLectorCartaDto estado();

    LecturaCartaDto leer(MultipartFile imagen);

    ResultadoImportacionCartaDto importar(ImportacionCartaDto importacion);
}
