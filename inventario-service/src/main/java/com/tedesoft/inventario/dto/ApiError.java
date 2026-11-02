package com.tedesoft.inventario.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

/** Formato común de error que devuelve el manejador global para cualquier respuesta 4xx/5xx. */
@Builder
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String mensaje,
        String ruta,
        List<CampoError> errores
) {

    /** Detalle de un campo inválido. */
    public record CampoError(String campo, String mensaje) {
    }
}
