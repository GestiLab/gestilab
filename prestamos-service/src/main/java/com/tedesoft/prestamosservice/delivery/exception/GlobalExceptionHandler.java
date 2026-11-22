package com.tedesoft.prestamosservice.delivery.exception;

import com.tedesoft.prestamosservice.delivery.security.RegistroAccesoRechazado;
import com.tedesoft.prestamosservice.delivery.security.RespuestaErrorSeguridad;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import com.tedesoft.prestamosservice.domain.exception.DependenciaRechazadaException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;
import com.tedesoft.prestamosservice.domain.exception.FotoInvalidaException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiErrorResponse> noEncontrado(RecursoNoEncontradoException ex) {
        log.warn("No encontrado: {}", ex.getMessage());
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ApiErrorResponse> reglaNegocio(ReglaNegocioException ex) {
        log.warn("Regla de negocio: {}", ex.getMessage());
        return construir(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(FotoInvalidaException.class)
    public ResponseEntity<ApiErrorResponse> fotoInvalida(FotoInvalidaException ex) {
        log.warn("Fotografía inválida: {}", ex.getMessage());
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    /**
     * Un archivo mayor que el máximo del multipart se rechaza antes de llegar al servicio. Spring lo
     * traduciría a 413; SCRUM-22 (CA-6) pide 400 con el motivo, igual que cualquier otra foto inválida.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> fotoDemasiadoGrande(MaxUploadSizeExceededException ex) {
        log.warn("Fotografía demasiado grande: {}", ex.getMessage());
        return construir(HttpStatus.BAD_REQUEST, "La fotografía supera el tamaño máximo de 5 MB", null);
    }

    @ExceptionHandler(DependenciaNoDisponibleException.class)
    public ResponseEntity<ApiErrorResponse> dependenciaNoDisponible(DependenciaNoDisponibleException ex) {
        // La causa (timeout, 5xx de inventario...) ya se registró donde ocurrió; al cliente solo el mensaje fijo.
        log.warn("Dependencia no disponible: {}", ex.getMessage());
        return construir(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), null);
    }

    @ExceptionHandler(DependenciaRechazadaException.class)
    public ResponseEntity<ApiErrorResponse> dependenciaRechazada(DependenciaRechazadaException ex) {
        // El código que devolvió la dependencia ya quedó en el log de quien la llamó; al cliente solo el mensaje fijo.
        log.warn("Dependencia rechazó la petición: {}", ex.getMessage());
        return construir(HttpStatus.BAD_GATEWAY, ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errores.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return construir(HttpStatus.BAD_REQUEST, "Datos de entrada inválidos", errores);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> jsonIlegible(HttpMessageNotReadableException ex) {
        return construir(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> parametroInvalido(MethodArgumentTypeMismatchException ex) {
        Map<String, String> errores = Map.of(ex.getName(), "El valor no tiene el formato esperado");
        return construir(HttpStatus.BAD_REQUEST, "Parámetro de ruta inválido", errores);
    }

    /**
     * 403 decidido dentro del controlador (un préstamo que no es de quien lo pide). Sin este handler el
     * {@code Exception} genérico de abajo lo convertiría en un 500. Responde igual que el 403 por rol de la cadena
     * de filtros: el mismo cuerpo, sin datos del recurso, y con su línea en el registro de rechazos.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> accesoDenegado(AccessDeniedException ex, HttpServletRequest peticion) {
        RegistroAccesoRechazado.registrar(peticion, HttpStatus.FORBIDDEN.value(), ex.getMessage(),
                SecurityContextHolder.getContext().getAuthentication());
        return construir(HttpStatus.FORBIDDEN, RespuestaErrorSeguridad.MENSAJE_403, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> generica(Exception ex) {
        // Spring ya trae el código correcto (404 ruta inexistente, 405...): se respeta
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return construir(status, status.getReasonPhrase(), null);
        }
        log.error("Excepción no manejada", ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error inesperado. Por favor contacta al administrador.", null);
    }

    private ResponseEntity<ApiErrorResponse> construir(HttpStatus status, String mensaje,
                                                       Map<String, String> errores) {
        // JSON fijo: si el cliente pidió una imagen (Accept: image/*), el error igual debe poder escribirse
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(status.value(), status.getReasonPhrase(), mensaje, errores));
    }
}