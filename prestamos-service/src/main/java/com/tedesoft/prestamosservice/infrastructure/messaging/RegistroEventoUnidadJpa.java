
package com.tedesoft.prestamosservice.infrastructure.messaging;

import com.tedesoft.prestamosservice.domain.clients.IEventoUnidadPendiente;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class RegistroEventoUnidadJpa
        implements IEventoUnidadPendiente {

    private final EventoUnidadPendienteRepository repository;

    @Override
    public void guardar(
            Long unidadId,
            String nuevoEstado,
            OffsetDateTime fecha) {

        EventoUnidadPendiente pendiente =
                new EventoUnidadPendiente(
                        unidadId,
                        nuevoEstado,
                        fecha
                );

        repository.save(pendiente);
    }
}
