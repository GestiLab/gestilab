package com.tedesoft.prestamosservice.infrastructure.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * El procesador se anota con {@code @Scheduled}, pero sin {@code @EnableScheduling} Spring crea la clase y
 * nunca la ejecuta: los eventos quedarían guardados para siempre. Esta prueba comprueba que SÍ se ejecuta sola.
 */
@SpringBootTest
@ActiveProfiles("test")
// Base H2 propia y contexto descartado al terminar: dos contextos con el procesador encendido sobre la MISMA
// base en memoria se roban los eventos (uno los marca como publicados con su publicador de mentira).
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:procesador-programado;DB_CLOSE_DELAY=-1",
        "gestilab.eventos.procesador.habilitado=true",
        "gestilab.rabbitmq.unidad.reintento-ms=100"
})
@DisplayName("El buzón de eventos se procesa solo, sin que nadie lo invoque")
class ProcesadorProgramadoTest {

    @Autowired EventoUnidadPendienteRepository repository;
    @MockBean UnidadCambioDeEstadoPublisher publisher;

    @Test
    @DisplayName("un evento pendiente se publica y queda marcado como publicado")
    void seProcesaSolo() {
        EventoUnidadPendiente pendiente = repository.save(
                new EventoUnidadPendiente(14L, "PRESTADA", OffsetDateTime.parse("2026-11-20T10:00:00-05:00")));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(pendiente.getId()).orElseThrow().estaPublicado()).isTrue());

        verify(publisher, atLeastOnce()).publicar(any());
    }
}
