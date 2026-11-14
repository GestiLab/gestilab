
package com.tedesoft.prestamosservice.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class UnidadCambioDeEstadoPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gestilab.rabbitmq.unidad.exchange}")
    private String exchange;

    @Value("${gestilab.rabbitmq.unidad.routing-key}")
    private String routingKey;

    public UnidadCambioDeEstadoPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper) {

        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publicar(UnidadCambioDeEstado evento) {

        try {
            // Convertir el evento a JSON
            byte[] contenido =
                    objectMapper.writeValueAsBytes(evento);

            MessageProperties propiedades =
                    new MessageProperties();

            propiedades.setContentType("application/json");
            propiedades.setContentEncoding("UTF-8");
            propiedades.setDeliveryMode(
                    MessageDeliveryMode.PERSISTENT
            );

            Message mensaje =
                    new Message(contenido, propiedades);

            // Identificar esta publicación
            CorrelationData correlacion =
                    new CorrelationData();

            // Enviar el mensaje
            rabbitTemplate.send(
                    exchange,
                    routingKey,
                    mensaje,
                    correlacion
            );

            // Esperar confirmación del servidor
            CorrelationData.Confirm confirmacion =
                    correlacion.getFuture()
                            .get(10, TimeUnit.SECONDS);

            if (!confirmacion.isAck()) {
                throw new IllegalStateException(
                        "RabbitMQ rechazó el evento: "
                                + confirmacion.getReason()
                );
            }

            // Comprobar que exista una cola de destino
            if (correlacion.getReturned() != null) {
                throw new IllegalStateException(
                        "El evento no llegó a ninguna cola: "
                                + correlacion.getReturned()
                                .getReplyText()
                );
            }

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "No se pudo convertir el evento a JSON", e
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Se interrumpió la confirmación de RabbitMQ", e
            );

        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(
                    "No se pudo confirmar la publicación", e
            );
        }
    }
}
