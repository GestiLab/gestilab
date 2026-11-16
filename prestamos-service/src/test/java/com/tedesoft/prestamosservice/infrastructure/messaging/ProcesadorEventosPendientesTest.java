package com.tedesoft.prestamosservice.infrastructure.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Buzón de eventos: ProcesadorEventosPendientes")
class ProcesadorEventosPendientesTest {

    @Mock EventoUnidadPendienteRepository repository;
    @Mock UnidadCambioDeEstadoPublisher publisher;

    private static EventoUnidadPendiente evento(long unidad, String estado) {
        return new EventoUnidadPendiente(unidad, estado, OffsetDateTime.parse("2026-11-20T10:00:00-05:00"));
    }

    @Test
    @DisplayName("publica los pendientes en orden y los marca como publicados")
    void publicaEnOrdenYMarca() {
        EventoUnidadPendiente a = evento(1, "PRESTADA");
        EventoUnidadPendiente b = evento(2, "DISPONIBLE");
        when(repository.findTop50ByPublicadoFalseOrderByIdAsc()).thenReturn(List.of(a, b));

        new ProcesadorEventosPendientes(repository, publisher).procesarPendientes();

        InOrder orden = inOrder(publisher, repository);
        orden.verify(publisher).publicar(a.aEvento());
        orden.verify(repository).save(a);
        orden.verify(publisher).publicar(b.aEvento());
        orden.verify(repository).save(b);
        assertThat(a.estaPublicado()).isTrue();
        assertThat(b.estaPublicado()).isTrue();
    }

    @Test
    @DisplayName("si uno falla, deja ese y los siguientes pendientes y conserva el orden")
    void siFallaSeDetieneYConservaElOrden() {
        EventoUnidadPendiente a = evento(1, "PRESTADA");
        EventoUnidadPendiente b = evento(2, "DISPONIBLE");
        EventoUnidadPendiente c = evento(3, "PRESTADA");
        when(repository.findTop50ByPublicadoFalseOrderByIdAsc()).thenReturn(List.of(a, b, c));
        doNothing().when(publisher).publicar(a.aEvento());
        doThrow(new IllegalStateException("RabbitMQ rechazó el evento")).when(publisher).publicar(b.aEvento());

        new ProcesadorEventosPendientes(repository, publisher).procesarPendientes();

        assertThat(a.estaPublicado()).isTrue();
        assertThat(b.estaPublicado()).isFalse();
        assertThat(c.estaPublicado()).isFalse();
        verify(publisher, never()).publicar(c.aEvento());
        verify(repository, never()).save(b);
        verify(repository, never()).save(c);
    }

    @Test
    @DisplayName("sin pendientes no publica nada")
    void sinPendientes() {
        when(repository.findTop50ByPublicadoFalseOrderByIdAsc()).thenReturn(List.of());

        new ProcesadorEventosPendientes(repository, publisher).procesarPendientes();

        verify(publisher, never()).publicar(any());
    }
}
