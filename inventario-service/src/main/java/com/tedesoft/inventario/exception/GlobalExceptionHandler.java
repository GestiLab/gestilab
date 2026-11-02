package com.tedesoft.inventario.exception;

import com.tedesoft.inventario.domain.ReglaPrestamoInvalidaException;
import com.tedesoft.inventario.dto.ApiError;
import com.tedesoft.inventario.dto.ApiError.CampoError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

/** Manejador global: todo error sale con el mismo formato {@link ApiError}. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> manejarValidacion(MethodArgumentNotValidException ex,
                                                      HttpServletRequest request) {
        List<CampoError> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new CampoError(e.getField(), e.getDefaultMessage()))
                .toList();
        return construir(HttpStatus.BAD_REQUEST, "Datos de entrada inválidos", request, errores);
    }

    @ExceptionHandler(ReglaPrestamoInvalidaException.class)
    public ResponseEntity<ApiError> manejarReglaInvalida(ReglaPrestamoInvalidaException ex,
                                                         HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "Datos de entrada inválidos", request,
                List.of(new CampoError(ex.getCampo(), ex.getMessage())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> manejarCuerpoIlegible(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> manejarTipoIncorrecto(MethodArgumentTypeMismatchException ex,
                                                          HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "El parámetro '" + ex.getName() + "' tiene un formato inválido",
                request, List.of(new CampoError(ex.getName(), "Formato inválido")));
    }

    @ExceptionHandler(TipoEquipoNoEncontradoException.class)
    public ResponseEntity<ApiError> manejarNoEncontrado(TipoEquipoNoEncontradoException ex,
                                                        HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> manejarInesperado(Exception ex, HttpServletRequest request) {
        // Excepciones propias de Spring MVC (405, 415, ruta inexistente...) conservan su código original.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return construir(status, errorResponse.getBody().getDetail(), request, List.of());
        }
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request, List.of());
    }

    private ResponseEntity<ApiError> construir(HttpStatus status, String mensaje, HttpServletRequest request,
                                               List<CampoError> errores) {
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .mensaje(mensaje)
                .ruta(request.getRequestURI())
                .errores(errores)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
