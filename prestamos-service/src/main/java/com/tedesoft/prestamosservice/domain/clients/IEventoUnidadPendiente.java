
package com.tedesoft.prestamosservice.domain.clients;

import java.time.OffsetDateTime;

public interface IEventoUnidadPendiente {

    void guardar(
            Long unidadId,
            String nuevoEstado,
            OffsetDateTime fecha
    );
}
