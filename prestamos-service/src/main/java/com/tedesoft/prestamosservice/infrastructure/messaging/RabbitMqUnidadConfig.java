
package com.tedesoft.prestamosservice.infrastructure.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqUnidadConfig {

    @Bean
    public TopicExchange unidadCambioDeEstadoExchange(
            @Value("${gestilab.rabbitmq.unidad.exchange}")
            String nombreExchange) {

        return new TopicExchange(nombreExchange, true, false);
    }
}
