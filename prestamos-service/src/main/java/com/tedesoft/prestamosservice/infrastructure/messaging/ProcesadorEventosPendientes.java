
package com.tedesoft.prestamosservice.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcesadorEventosPendientes {

    private final EventoUnidadPendienteRepository repository;
    private final UnidadCambioDeEstadoPublisher publisher;

    @Scheduled(fixedDelayString =
            "${gestilab.rabbitmq.unidad.reintento-ms:5000}")
    public void procesarPendientes() {

        List<EventoUnidadPendiente> pendientes =
                repository.findTop50ByPublicadoFalseOrderByIdAsc();

        for (EventoUnidadPendiente pendiente : pendientes) {

            try {
                publisher.publicar(pendiente.aEvento());

                pendiente.marcarPublicado();
                repository.save(pendiente);

                log.info(
                        "Evento {} publicado para unidad {}",
                        pendiente.getId(),
                        pendiente.aEvento().unidadId()
                );

            } catch (Exception e) {

                log.warn(
                        "No se pudo procesar el evento {}. "
                                + "Se reintentará.",
                        pendiente.getId(),
                        e
                );

                // Detener el lote para mantener el orden
                break;
            }
        }
    }
}
