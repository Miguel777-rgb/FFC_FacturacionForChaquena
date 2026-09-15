package com.chaquena.backend_logistica.archivos.service;

import com.chaquena.backend_logistica.archivos.domain.Archivo;
import com.chaquena.backend_logistica.archivos.dto.ArchivoDto;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ArchivoService {

    /** Lo que hace falta para servir un archivo. */
    record Contenido(Resource recurso, String tipoContenido, long tamanoBytes) {
    }

    ArchivoDto subir(MultipartFile archivo);

    Contenido leer(UUID id);

    /** La fila, para colgarla de un platillo o del local. 404 si no existe. */
    Archivo obtener(UUID id);

    /**
     * Borra la fila ya y el fichero cuando la transaccion confirme: si algo la
     * deshace, la foto sigue en disco y el platillo no se queda con un hueco.
     */
    void eliminar(UUID id);
}
