package com.tedesoft.inventarioservice.delivery.exception;

import com.tedesoft.inventarioservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.inventarioservice.domain.exception.ParametroInvalidoException;
import com.tedesoft.inventarioservice.domain.exception.ReglaNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
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

    @ExceptionHandler(ParametroInvalidoException.class)
    public ResponseEntity<ApiErrorResponse> parametroInvalido(ParametroInvalidoException ex) {
        Map<String, String> errores = Map.of(ex.getCampo(), ex.getMessage());
        return construir(HttpStatus.BAD_REQUEST, "Parámetros de consulta inválidos", errores);
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
        // ?page=abc es un parámetro de consulta: mismo mensaje que las demás validaciones del catálogo (CA-8)
        String mensaje = ex.getParameter().hasParameterAnnotation(RequestParam.class)
                ? "Parámetros de consulta inválidos"
                : "Parámetro de ruta inválido";
        return construir(HttpStatus.BAD_REQUEST, mensaje, errores);
    }

    // Red de seguridad ante la restricción única de la BD (dos peticiones simultáneas con el mismo nombre)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> integridad(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return construir(HttpStatus.CONFLICT, "El dato ya existe o viola una restricción de integridad", null);
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
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(status.value(), status.getReasonPhrase(), mensaje, errores));
    }
}