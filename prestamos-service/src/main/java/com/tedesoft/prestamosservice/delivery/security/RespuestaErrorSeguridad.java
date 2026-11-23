package com.tedesoft.prestamosservice.delivery.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tedesoft.prestamosservice.delivery.exception.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escribe los 401 y 403 con el cuerpo común ({@code docs/guia-errores.md}). Estos rechazos ocurren en la cadena
 * de filtros, antes de llegar a Spring MVC, así que el {@code GlobalExceptionHandler} no los ve: se arman aquí
 * con la misma clase {@link ApiErrorResponse}.
 *
 * <p>Los mensajes son fijos a propósito: no mencionan el recurso pedido ni el motivo exacto del rechazo.
 */
public class RespuestaErrorSeguridad {

    public static final String MENSAJE_401 = "Se requiere un token válido";
    public static final String MENSAJE_403 = "No tienes permiso para realizar esta operación";

    private final ObjectMapper objectMapper;

    public RespuestaErrorSeguridad(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void escribir(HttpServletResponse respuesta, HttpStatus estado, String mensaje) throws IOException {
        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(respuesta.getOutputStream(),
                new ApiErrorResponse(estado.value(), estado.getReasonPhrase(), mensaje));
    }
}
