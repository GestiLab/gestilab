package com.tedesoft.prestamosservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;
import com.tedesoft.prestamosservice.infrastructure.messaging.EventoUnidadPendienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * SCRUM-19, CA-3, CA-4 y CA-11 contra RabbitMQ real: el evento llega a una cola enlazada con el formato
 * acordado ({@code unidadId}, {@code nuevoEstado}, {@code fecha}). Si Docker no está disponible se omite.
 */
@SpringBootTest(properties = {
        "gestilab.eventos.procesador.habilitado=true",
        "gestilab.rabbitmq.unidad.reintento-ms=200"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("SCRUM-19: el evento UnidadCambioDeEstado llega a RabbitMQ real")
class EventoRabbitMqTest {

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    private static final String COLA = "prueba.inventario.unidad-estado";

    @Value("${gestilab.rabbitmq.unidad.exchange}") String exchange;
    @Value("${gestilab.rabbitmq.unidad.routing-key}") String routingKey;
    @Autowired IPrestamoService servicio;
    @Autowired IPrestamoDao dao;
    @Autowired EventoUnidadPendienteRepository eventos;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitTemplate rabbit;
    @Autowired TopicExchange unidadCambioDeEstadoExchange;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void limpiar() {
        eventos.deleteAll();
        dao.deleteAll();
        admin.deleteQueue(COLA);
    }

    private void enlazarColaDelConsumidor() {
        Queue cola = new Queue(COLA, false);
        admin.declareQueue(cola);
        Binding enlace = BindingBuilder.bind(cola).to(unidadCambioDeEstadoExchange).with(routingKey);
        admin.declareBinding(enlace);
    }

    @Test
    @DisplayName("CA-3 y CA-11: al entregar llega UnidadCambioDeEstado con unidad, estado PRESTADA y fecha ISO-8601")
    void entregarPublicaElEvento() throws Exception {
        enlazarColaDelConsumidor();
        Prestamo p = dao.save(Prestamo.solicitar(14L, 2L, "ana", 7, LocalDate.of(2026, 11, 14)));

        servicio.entregar(p.getId());

        Message mensaje = esperarMensaje();
        assertThat(mensaje.getMessageProperties().getContentType()).isEqualTo("application/json");
        JsonNode json = mapper.readTree(mensaje.getBody());
        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder("unidadId", "nuevoEstado", "fecha");
        assertThat(json.get("unidadId").asLong()).isEqualTo(14L);
        assertThat(json.get("nuevoEstado").asText()).isEqualTo("PRESTADA");
        assertThat(OffsetDateTime.parse(json.get("fecha").asText())).isNotNull();
    }

    @Test
    @DisplayName("CA-4: al recibir llega el estado DISPONIBLE")
    void recibirPublicaDisponible() throws Exception {
        enlazarColaDelConsumidor();
        Prestamo p = dao.save(Prestamo.solicitar(14L, 2L, "ana", 7, LocalDate.of(2026, 11, 14)));
        servicio.entregar(p.getId());
        esperarMensaje();

        servicio.recibir(p.getId());

        JsonNode json = mapper.readTree(esperarMensaje().getBody());
        assertThat(json.get("nuevoEstado").asText()).isEqualTo("DISPONIBLE");
        assertThat(json.get("unidadId").asLong()).isEqualTo(14L);
    }

    @Test
    @DisplayName("Sin cola enlazada el evento NO se pierde: queda pendiente y sale cuando el consumidor enlaza su cola")
    void sinColaElEventoSeConserva() throws Exception {
        Prestamo p = dao.save(Prestamo.solicitar(14L, 2L, "ana", 7, LocalDate.of(2026, 11, 14)));

        servicio.entregar(p.getId());

        Thread.sleep(1500);
        assertThat(eventos.findTop50ByPublicadoFalseOrderByIdAsc()).hasSize(1);

        enlazarColaDelConsumidor();
        JsonNode json = mapper.readTree(esperarMensaje().getBody());
        assertThat(json.get("nuevoEstado").asText()).isEqualTo("PRESTADA");
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(eventos.findTop50ByPublicadoFalseOrderByIdAsc()).isEmpty());
    }

    private Message esperarMensaje() {
        Message[] recibido = new Message[1];
        await().atMost(Duration.ofSeconds(15)).until(() -> (recibido[0] = rabbit.receive(COLA)) != null);
        return recibido[0];
    }
}
