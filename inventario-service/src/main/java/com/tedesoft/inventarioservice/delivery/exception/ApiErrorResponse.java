package com.tedesoft.inventarioservice.delivery.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {
    private final int status;
    private final String error;
    private final String mensaje;
    private final LocalDateTime timestamp = LocalDateTime.now();
    private final Map<String, String> errores;   // campo -> mensaje (solo en 400)

    public ApiErrorResponse(int status, String error, String mensaje) {
        this(status, error, mensaje, null);
    }
    public ApiErrorResponse(int status, String error, String mensaje, Map<String, String> errores) {
        this.status = status; this.error = error; this.mensaje = mensaje; this.errores = errores;
    }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMensaje() { return mensaje; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Map<String, String> getErrores() { return errores; }
}