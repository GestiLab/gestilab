package com.tedesoft.inventarioservice.delivery.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.services.UnidadEstadoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consume el evento {@code UnidadCambioDeEstado} de la cola propia de inventario (SCRUM-21, CA-11).
 *
 * <p>Un mensaje que no se puede interpretar se descarta con un registro y se confirma: si se lanzara una
 * excepción, RabbitMQ lo reentregaría sin parar y los eventos siguientes no se consumirían. Solo un fallo
 * transitorio (por ejemplo la base de datos caída) sale como excepción, y el mensaje se reintenta.
 * Se apaga con {@code gestilab.eventos.consumidor.habilitado=false} (las pruebas con H2 no tienen broker).
 */
@Component
@ConditionalOnProperty(name = "gestilab.eventos.consumidor.habilitado", havingValue = "true", matchIfMissing = true)
public class UnidadCambioDeEstadoListener {

    private static final Logger log = LoggerFactory.getLogger(UnidadCambioDeEstadoListener.class);
    private static final int MAXIMO_CUERPO_EN_REGISTRO = 300;

    private final ObjectMapper objectMapper;
    private final UnidadEstadoService servicio;

    public UnidadCambioDeEstadoListener(ObjectMapper objectMapper, UnidadEstadoService servicio) {
        this.objectMapper = objectMapper;
        this.servicio = servicio;
    }

    @RabbitListener(queues = "${gestilab.rabbitmq.unidad.cola}")
    public void alRecibir(Message mensaje) {
        procesar(new String(mensaje.getBody(), StandardCharsets.UTF_8));
    }

    /** Interpreta el cuerpo y, si es válido, lo entrega al dominio. Separado de AMQP para probarlo sin broker. */
    public void procesar(String cuerpo) {
        UnidadCambioDeEstadoEvento evento;
        try {
            evento = objectMapper.readValue(cuerpo, UnidadCambioDeEstadoEvento.class);
        } catch (JsonProcessingException e) {
            log.warn("Evento descartado: el mensaje no cumple el formato ({}). Cuerpo: {}",
                    e.getOriginalMessage(), recortar(cuerpo));
            return;
        }

        if (evento == null || evento.unidadId() == null || evento.fecha() == null
                || evento.nuevoEstado() == null || evento.nuevoEstado().isBlank()) {
            log.warn("Evento descartado: faltan datos (unidadId, nuevoEstado o fecha). Cuerpo: {}", recortar(cuerpo));
            return;
        }

        if (!EstadoUnidad.esValido(evento.nuevoEstado())) {
            log.warn("Evento descartado: el estado \"{}\" no existe (válidos: {}). Unidad {}",
                    evento.nuevoEstado(), EstadoUnidad.permitidosComoTexto(), evento.unidadId());
            return;
        }

        servicio.aplicarCambioDeEstado(evento.unidadId(), EstadoUnidad.desdeTexto(evento.nuevoEstado()),
                evento.fecha().toInstant());
    }

    private static String recortar(String cuerpo) {
        if (cuerpo == null) {
            return "null";
        }
        return cuerpo.length() <= MAXIMO_CUERPO_EN_REGISTRO
                ? cuerpo
                : cuerpo.substring(0, MAXIMO_CUERPO_EN_REGISTRO) + "...";
    }
}
