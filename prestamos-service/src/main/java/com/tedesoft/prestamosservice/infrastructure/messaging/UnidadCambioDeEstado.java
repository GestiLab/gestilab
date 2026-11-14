
package com.tedesoft.prestamosservice.infrastructure.messaging;

import java.time.OffsetDateTime;

public record UnidadCambioDeEstado(
        Long unidadId,
        String nuevoEstado,
        OffsetDateTime fecha
) {
}
