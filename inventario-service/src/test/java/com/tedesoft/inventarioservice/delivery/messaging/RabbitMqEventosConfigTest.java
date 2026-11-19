package com.tedesoft.inventarioservice.delivery.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.assertj.core.api.Assertions.assertThat;

/** CA-11 sin broker: qué declara inventario (exchange, cola propia y enlace) cuando se conecta. */
class RabbitMqEventosConfigTest {

    private final RabbitMqEventosConfig config = new RabbitMqEventosConfig();

    @Test
    @DisplayName("CA-11: el exchange es gestilab.unidades.eventos, de tipo topic y durable, igual que el de prestamos-service")
    void exchange() {
        TopicExchange exchange = config.unidadCambioDeEstadoExchange("gestilab.unidades.eventos");

        assertThat(exchange.getName()).isEqualTo("gestilab.unidades.eventos");
        assertThat(exchange.getType()).isEqualTo(ExchangeTypes.TOPIC);
        assertThat(exchange.isDurable()).isTrue();
        assertThat(exchange.isAutoDelete()).isFalse();
    }

    @Test
    @DisplayName("CA-11/CA-12: la cola propia es durable, para que los eventos esperen mientras inventario está apagado")
    void cola() {
        Queue cola = config.colaUnidadCambioDeEstado("inventario.unidad-estado");

        assertThat(cola.getName()).isEqualTo("inventario.unidad-estado");
        assertThat(cola.isDurable()).isTrue();
        assertThat(cola.isExclusive()).isFalse();
        assertThat(cola.isAutoDelete()).isFalse();
    }

    @Test
    @DisplayName("CA-11: la cola se enlaza al exchange con la routing key unidad.estado.cambiado")
    void enlace() {
        TopicExchange exchange = config.unidadCambioDeEstadoExchange("gestilab.unidades.eventos");
        Queue cola = config.colaUnidadCambioDeEstado("inventario.unidad-estado");

        Binding enlace = config.enlaceUnidadCambioDeEstado(cola, exchange, "unidad.estado.cambiado");

        assertThat(enlace.getExchange()).isEqualTo("gestilab.unidades.eventos");
        assertThat(enlace.getRoutingKey()).isEqualTo("unidad.estado.cambiado");
        assertThat(enlace.getDestination()).isEqualTo("inventario.unidad-estado");
        assertThat(enlace.isDestinationQueue()).isTrue();
    }
}
