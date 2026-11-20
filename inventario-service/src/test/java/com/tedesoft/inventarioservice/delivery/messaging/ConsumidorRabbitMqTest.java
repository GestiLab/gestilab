package com.tedesoft.inventarioservice.delivery.messaging;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.repositories.TipoEquipoRepository;
import com.tedesoft.inventarioservice.domain.repositories.UnidadRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * SCRUM-21, CA-3 y CA-11 contra RabbitMQ real: inventario declara su cola, la enlaza y aplica el evento en
 * menos de 5 segundos. Si Docker no está disponible se omite (los CA-1 a CA-6 ya se prueban sin broker).
 */
@SpringBootTest(properties = {
        // Base H2 propia: no comparte datos con las otras pruebas de integración
        "spring.datasource.url=jdbc:h2:mem:inventario-evento-rabbitmq;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "gestilab.eventos.consumidor.habilitado=true"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("SCRUM-21: inventario consume UnidadCambioDeEstado desde RabbitMQ real")
class ConsumidorRabbitMqTest {

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    private static final Duration LIMITE = Duration.ofSeconds(5);

    @Value("${gestilab.rabbitmq.unidad.exchange}")
    String exchange;
    @Value("${gestilab.rabbitmq.unidad.routing-key}")
    String routingKey;
    @Value("${gestilab.rabbitmq.unidad.cola}")
    String cola;

    @Autowired
    RabbitTemplate rabbit;
    @Autowired
    RabbitAdmin admin;
    @Autowired
    UnidadRepository unidadRepository;
    @Autowired
    TipoEquipoRepository tipoEquipoRepository;

    private TipoEquipo tipo;

    @BeforeEach
    void preparar() {
        esperarConsumidor();
        admin.purgeQueue(cola);
        limpiar();
        tipo = tipoEquipoRepository.save(TipoEquipo.crear("Multímetro", "Mide voltaje", 7, false));
    }

    @AfterEach
    void limpiar() {
        unidadRepository.deleteAll();
        tipoEquipoRepository.deleteAll();
    }

    private void esperarConsumidor() {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            QueueInformation info = admin.getQueueInfo(cola);
            assertThat(info).as("la cola propia de inventario").isNotNull();
            assertThat(info.getConsumerCount()).isEqualTo(1);
        });
    }

    private void publicar(String json) {
        Message mensaje = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbit.send(exchange, routingKey, mensaje);
    }

    private static String evento(long unidadId, String estado, String fecha) {
        return "{\"unidadId\":" + unidadId + ",\"nuevoEstado\":\"" + estado + "\",\"fecha\":\"" + fecha + "\"}";
    }

    private EstadoUnidad estadoDe(Unidad unidad) {
        return unidadRepository.findById(unidad.getId()).orElseThrow().getEstado();
    }

    @Test
    @DisplayName("CA-11: al arrancar declara su cola, con un consumidor, y recibe lo que llega al exchange con la routing key")
    void colaDeclaradaYEnlazada() {
        QueueInformation info = admin.getQueueInfo(cola);

        assertThat(info).isNotNull();
        assertThat(info.getName()).isEqualTo(cola);
        assertThat(info.getConsumerCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("CA-1, CA-2 y CA-3: prestada y devuelta se reflejan en menos de 5 segundos")
    void prestadaYDevueltaEnMenosDeCincoSegundos() {
        Unidad unidad = unidadRepository.save(Unidad.crear(tipo, "MUL-014", EstadoUnidad.DISPONIBLE, null));

        publicar(evento(unidad.getId(), "PRESTADA", "2026-11-20T10:00:00-05:00"));
        await().atMost(LIMITE).untilAsserted(() -> assertThat(estadoDe(unidad)).isEqualTo(EstadoUnidad.PRESTADA));

        publicar(evento(unidad.getId(), "DISPONIBLE", "2026-11-20T10:01:00-05:00"));
        await().atMost(LIMITE).untilAsserted(() -> assertThat(estadoDe(unidad)).isEqualTo(EstadoUnidad.DISPONIBLE));
    }

    @Test
    @DisplayName("CA-4, CA-5, CA-6 y CA-9: los mensajes que se descartan no detienen el consumo de los siguientes")
    void losDescartesNoBloqueanLaCola() {
        Unidad primera = unidadRepository.save(Unidad.crear(tipo, "MUL-014", EstadoUnidad.DISPONIBLE, null));
        Unidad marcador = unidadRepository.save(Unidad.crear(tipo, "MUL-015", EstadoUnidad.DISPONIBLE, null));

        publicar(evento(999, "PRESTADA", "2026-11-20T10:00:00-05:00"));                       // CA-5
        publicar(evento(primera.getId(), "perdida", "2026-11-20T10:00:00-05:00"));            // CA-6
        publicar("esto no es json");                                                          // mensaje ilegible
        publicar(evento(primera.getId(), "PRESTADA", "2026-11-20T10:01:00-05:00"));           // se aplica
        publicar(evento(primera.getId(), "PRESTADA", "2026-11-20T10:01:00-05:00"));           // CA-4 repetido
        publicar(evento(primera.getId(), "DISPONIBLE", "2026-11-20T10:00:00-05:00"));         // CA-9 anterior
        publicar(evento(marcador.getId(), "PRESTADA", "2026-11-20T10:02:00-05:00"));          // marcador

        // La cola es FIFO con un solo consumidor: cuando el marcador se aplica, todo lo anterior ya se procesó
        await().atMost(LIMITE).untilAsserted(() -> assertThat(estadoDe(marcador)).isEqualTo(EstadoUnidad.PRESTADA));
        assertThat(estadoDe(primera)).isEqualTo(EstadoUnidad.PRESTADA);
    }
}
