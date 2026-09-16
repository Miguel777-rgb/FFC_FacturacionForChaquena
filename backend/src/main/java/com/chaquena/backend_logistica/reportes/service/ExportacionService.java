package com.chaquena.backend_logistica.reportes.service;

import com.chaquena.backend_logistica.reportes.dto.FormatoExportacionEnum;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;

/** Los reportes que se bajan como archivo, en pdf o xlsx y en el idioma de la pantalla. */
public interface ExportacionService {

    record ArchivoExportado(byte[] contenido, String nombre, FormatoExportacionEnum formato) {
    }

    ArchivoExportado ventas(ZonedDateTime desde, ZonedDateTime hasta, FormatoExportacionEnum formato, Locale locale);

    ArchivoExportado productos(ZonedDateTime desde, ZonedDateTime hasta, FormatoExportacionEnum formato, Locale locale);

    ArchivoExportado inventario(FormatoExportacionEnum formato, Locale locale);

    ArchivoExportado asistencia(LocalDate desde, LocalDate hasta, FormatoExportacionEnum formato, Locale locale);
}
