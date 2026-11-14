
package com.tedesoft.prestamosservice.infrastructure.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventoUnidadPendienteRepository
        extends JpaRepository<EventoUnidadPendiente, Long> {

    List<EventoUnidadPendiente>
    findTop50ByPublicadoFalseOrderByIdAsc();
}
