package com.chaquena.backend_logistica.shared.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.ZonedDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ErrorResponseDto construir(HttpStatus status, String error, String mensaje,
            List<String> detalles, HttpServletRequest request) {
        return ErrorResponseDto.builder()
                .timestamp(ZonedDateTime.now())
                .status(status.value())
                .error(error)
                .message(mensaje)
                .details(detalles)
                .path(request.getRequestURI())
                .build();
    }

    @ExceptionHandler({ EntityNotFoundException.class, RecursoNoEncontradoException.class })
    public ResponseEntity<ErrorResponseDto> noEncontrado(RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(construir(HttpStatus.NOT_FOUND, "Recurso No Encontrado", ex.getMessage(), null, request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> peticionInvalida(IllegalArgumentException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(construir(HttpStatus.BAD_REQUEST, "Peticion Invalida", ex.getMessage(), null, request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> validacion(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(construir(HttpStatus.BAD_REQUEST, "Error de Validacion de Datos",
                        "Uno o mas campos contienen errores de validacion", detalles, request));
    }

    /**
     * Transicion de estado ilegal, duplicado o baja bloqueada por dependencias.
     * El frontend distingue esto de un 400 para poder ofrecer una accion
     * correctiva en lugar de pedir que se corrija el formulario.
     */
    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorResponseDto> conflicto(ConflictoException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(construir(HttpStatus.CONFLICT, "Conflicto con el Estado Actual", ex.getMessage(), null,
                        request));
    }

    /**
     * La comanda es sintacticamente valida pero no hay insumos para prepararla.
     * Devuelve el detalle de faltantes para que el mozo elija cambiar, esperar,
     * suplir o cancelar.
     */
    @ExceptionHandler(StockInsuficienteException.class)
    public ResponseEntity<ErrorResponseDto> stockInsuficiente(StockInsuficienteException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(construir(HttpStatus.UNPROCESSABLE_ENTITY, "Stock Insuficiente", ex.getMessage(),
                        ex.getFaltantes(), request));
    }

    /**
     * Credenciales que no dan acceso: token de Google invalido, expirado o
     * emitido para otra aplicacion, o una identidad valida que no corresponde a
     * ningun trabajador dado de alta. Distinto del 403, que significa que el
     * usuario si esta identificado pero su cargo no alcanza.
     */
    @ExceptionHandler(AutenticacionFallidaException.class)
    public ResponseEntity<ErrorResponseDto> autenticacionFallida(AutenticacionFallidaException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(construir(HttpStatus.UNAUTHORIZED, "No Autenticado", ex.getMessage(), null, request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> accesoDenegado(AccessDeniedException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(construir(HttpStatus.FORBIDDEN, "Acceso Denegado",
                        "Tu cargo no tiene permiso para esta operacion.", null, request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> integridad(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(construir(HttpStatus.CONFLICT, "Violacion de Integridad",
                        "El dato ya existe o esta referenciado por otro registro.", null, request));
    }

    /**
     * Ruta inexistente, o un identificador vacio que deja la URL colgando.
     * Sin esto caia en el manejador generico y salia como 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDto> rutaInexistente(NoResourceFoundException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(construir(HttpStatus.NOT_FOUND, "Ruta No Encontrada",
                        "No existe el recurso " + request.getRequestURI() + ".", null, request));
    }

    /**
     * Un valor de la URL que no encaja con el tipo que espera el controlador:
     * casi siempre un UUID mal copiado, o una variable de Postman que se quedo
     * sin resolver y llego literalmente como "null".
     *
     * <p>Es un error de quien llama, no del servidor. Sin este manejador salia
     * como 500 y el frontend no podia distinguir un id mal escrito de una caida
     * real del backend.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> parametroConTipoInvalido(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String esperado = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName()
                : "otro tipo";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(construir(HttpStatus.BAD_REQUEST, "Parametro Invalido",
                        "El valor '" + ex.getValue() + "' no sirve para '" + ex.getName()
                                + "': se esperaba " + esperado + ".",
                        null, request));
    }

    /**
     * Falta una cabecera o un parametro que el endpoint declara obligatorio.
     *
     * <p>Sucede, por ejemplo, cuando un cliente llama a {@code /auth/google} sin
     * la cabecera {@code Authorization}. Es una peticion mal formada, no un
     * fallo del servidor, y sin este manejador salia como 500.
     */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ErrorResponseDto> faltaCabeceraOParametro(ServletRequestBindingException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(construir(HttpStatus.BAD_REQUEST, "Falta un Dato Obligatorio en la Peticion",
                        ex.getMessage(), null, request));
    }

    /**
     * Cuerpo que no se puede leer: JSON mal formado, o un valor que no
     * corresponde a ningun elemento del enum. Tambien es culpa de quien llama.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> cuerpoIlegible(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        String causa = ex.getMostSpecificCause().getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(construir(HttpStatus.BAD_REQUEST, "Cuerpo de la Peticion Invalido",
                        "No se pudo interpretar el cuerpo de la peticion.",
                        causa != null ? List.of(causa) : null, request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> generico(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(construir(HttpStatus.INTERNAL_SERVER_ERROR, "Error Interno del Servidor",
                        ex.getMessage(), null, request));
    }
}
