package com.tedesoft.inventarioservice.delivery.messaging;

import java.time.OffsetDateTime;

/**
 * Evento que publica prestamos-service (formato en el README de ese servicio):
 * {@code {"unidadId": 14, "nuevoEstado": "PRESTADA", "fecha": "2026-11-20T10:00:00-05:00"}}.
 * El estado llega como texto para poder descartar uno desconocido con un registro, sin romper el consumo.
 */
public record UnidadCambioDeEstadoEvento(Long unidadId, String nuevoEstado, OffsetDateTime fecha) {
}
