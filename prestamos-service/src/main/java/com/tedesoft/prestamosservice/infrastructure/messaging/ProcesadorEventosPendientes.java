
package com.tedesoft.prestamosservice.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Vacía el buzón de eventos pendientes hacia RabbitMQ. Se apaga con
 * {@code gestilab.eventos.procesador.habilitado=false} (las pruebas con H2 no tienen broker).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "gestilab.eventos.procesador.habilitado", havingValue = "true", matchIfMissing = true)
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

                // Solo el mensaje: con el broker caído o sin cola enlazada se repite cada ciclo
                // y una traza completa llenaría el registro.
                log.warn("No se pudo publicar el evento {} ({}). Se reintentará.",
                        pendiente.getId(), e.getMessage());

                // Detener el lote para mantener el orden
                break;
            }
        }
    }
}
