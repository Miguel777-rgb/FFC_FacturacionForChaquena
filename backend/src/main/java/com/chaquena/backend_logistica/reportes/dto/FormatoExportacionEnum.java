package com.chaquena.backend_logistica.reportes.dto;

import org.springframework.http.MediaType;

/** En que archivo se baja un reporte. */
public enum FormatoExportacionEnum {

    PDF("application/pdf", "pdf"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    private final String tipoDeContenido;
    private final String extension;

    FormatoExportacionEnum(String tipoDeContenido, String extension) {
        this.tipoDeContenido = tipoDeContenido;
        this.extension = extension;
    }

    public MediaType tipoDeContenido() {
        return MediaType.parseMediaType(tipoDeContenido);
    }

    public String extension() {
        return extension;
    }
}
