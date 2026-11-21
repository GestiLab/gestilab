package com.tedesoft.inventarioservice.delivery.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cola propia de inventario enlazada al exchange de eventos de unidades (SCRUM-21, CA-11). RabbitMQ la
 * declara al abrir la primera conexión, así que el servicio arranca aunque el broker no esté disponible.
 *
 * <p>El exchange lo declara prestamos-service; aquí se declara igual (mismos argumentos, idempotente) para
 * que el orden de arranque de los dos servicios no importe. La cola es durable: los eventos que lleguen
 * mientras inventario está apagado esperan en ella.
 */
@Configuration
public class RabbitMqEventosConfig {

    @Bean
    public TopicExchange unidadCambioDeEstadoExchange(
            @Value("${gestilab.rabbitmq.unidad.exchange}") String nombreExchange) {
        return new TopicExchange(nombreExchange, true, false);
    }

    @Bean
    public Queue colaUnidadCambioDeEstado(@Value("${gestilab.rabbitmq.unidad.cola}") String nombreCola) {
        return QueueBuilder.durable(nombreCola).build();
    }

    @Bean
    public Binding enlaceUnidadCambioDeEstado(
            Queue colaUnidadCambioDeEstado,
            TopicExchange unidadCambioDeEstadoExchange,
            @Value("${gestilab.rabbitmq.unidad.routing-key}") String routingKey) {
        return BindingBuilder.bind(colaUnidadCambioDeEstado).to(unidadCambioDeEstadoExchange).with(routingKey);
    }
}
