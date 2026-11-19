package com.tedesoft.inventarioservice.delivery.messaging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.services.UnidadEstadoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** CA-8: el consumo con un mensaje simulado, sin broker real. */
@ExtendWith(MockitoExtension.class)
class UnidadCambioDeEstadoListenerTest {

    private static final String EVENTO =
            "{\"unidadId\":14,\"nuevoEstado\":\"PRESTADA\",\"fecha\":\"2026-11-20T10:00:00-05:00\"}";
    private static final Instant ESA_FECHA_EN_UTC = Instant.parse("2026-11-20T15:00:00Z");

    @Mock
    private UnidadEstadoService servicio;

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private UnidadCambioDeEstadoListener listener;
    private Logger logDelListener;
    private ListAppender<ILoggingEvent> registros;

    @BeforeEach
    void preparar() {
        listener = new UnidadCambioDeEstadoListener(objectMapper, servicio);
        logDelListener = (Logger) LoggerFactory.getLogger(UnidadCambioDeEstadoListener.class);
        registros = new ListAppender<>();
        registros.start();
        logDelListener.addAppender(registros);
    }

    @AfterEach
    void soltar() {
        logDelListener.detachAppender(registros);
    }

    @Test
    @DisplayName("CA-1: un evento válido llega al dominio con la unidad, el estado y la fecha en UTC")
    void eventoValido() {
        listener.procesar(EVENTO);

        verify(servicio).aplicarCambioDeEstado(14L, EstadoUnidad.PRESTADA, ESA_FECHA_EN_UTC);
    }

    @Test
    @DisplayName("el mensaje de RabbitMQ se lee desde su cuerpo en UTF-8")
    void mensajeDeRabbit() {
        Message mensaje = new Message(EVENTO.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        listener.alRecibir(mensaje);

        verify(servicio).aplicarCambioDeEstado(14L, EstadoUnidad.PRESTADA, ESA_FECHA_EN_UTC);
    }

    @Test
    @DisplayName("el estado se acepta en minúsculas y los campos extra se ignoran")
    void toleranteConElFormato() {
        listener.procesar(
                "{\"unidadId\":14,\"nuevoEstado\":\"disponible\",\"fecha\":\"2026-11-20T15:00:00Z\",\"otro\":true}");

        verify(servicio).aplicarCambioDeEstado(14L, EstadoUnidad.DISPONIBLE, ESA_FECHA_EN_UTC);
    }

    @Test
    @DisplayName("un estado que existe pero no es PRESTADA ni DISPONIBLE llega al dominio, que lo descarta")
    void estadoQueElDominioDescarta() {
        listener.procesar(
                "{\"unidadId\":14,\"nuevoEstado\":\"EN_REVISION\",\"fecha\":\"2026-11-20T15:00:00Z\"}");

        verify(servicio).aplicarCambioDeEstado(14L, EstadoUnidad.EN_REVISION, ESA_FECHA_EN_UTC);
    }

    static Stream<Arguments> mensajesInvalidos() {
        return Stream.of(
                Arguments.of("CA-6: estado que no existe",
                        "{\"unidadId\":14,\"nuevoEstado\":\"perdida\",\"fecha\":\"2026-11-20T15:00:00Z\"}"),
                Arguments.of("estado en blanco",
                        "{\"unidadId\":14,\"nuevoEstado\":\"  \",\"fecha\":\"2026-11-20T15:00:00Z\"}"),
                Arguments.of("sin estado", "{\"unidadId\":14,\"fecha\":\"2026-11-20T15:00:00Z\"}"),
                Arguments.of("sin unidad", "{\"nuevoEstado\":\"PRESTADA\",\"fecha\":\"2026-11-20T15:00:00Z\"}"),
                Arguments.of("sin fecha", "{\"unidadId\":14,\"nuevoEstado\":\"PRESTADA\"}"),
                Arguments.of("fecha que no es ISO-8601",
                        "{\"unidadId\":14,\"nuevoEstado\":\"PRESTADA\",\"fecha\":\"ayer\"}"),
                Arguments.of("unidad que no es un número",
                        "{\"unidadId\":\"abc\",\"nuevoEstado\":\"PRESTADA\",\"fecha\":\"2026-11-20T15:00:00Z\"}"),
                Arguments.of("texto que no es JSON", "esto no es json"),
                Arguments.of("cuerpo vacío", ""),
                Arguments.of("JSON null", "null"),
                Arguments.of("JSON que no es un objeto", "[]")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mensajesInvalidos")
    @DisplayName("CA-6: un mensaje inválido se descarta con un registro, sin excepción y sin tocar el dominio")
    void mensajeInvalido(String caso, String cuerpo) {
        assertThatCode(() -> listener.procesar(cuerpo)).doesNotThrowAnyException();

        verifyNoInteractions(servicio);
        assertThat(registros.list).anyMatch(e -> e.getLevel() == Level.WARN);
    }

    @Test
    @DisplayName("CA-6: el registro de un estado inexistente lista los estados válidos")
    void registroConLosEstadosValidos() {
        listener.procesar("{\"unidadId\":14,\"nuevoEstado\":\"perdida\",\"fecha\":\"2026-11-20T15:00:00Z\"}");

        assertThat(registros.list).anyMatch(e -> e.getFormattedMessage().contains("perdida")
                && e.getFormattedMessage().contains("FUERA_DE_SERVICIO"));
    }
}
