package com.tedesoft.inventarioservice.domain.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.ResultadoEventoEstado;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.repositories.UnidadRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Reglas del evento con el repositorio simulado: sin base de datos, sin broker y sin reloj. */
@ExtendWith(MockitoExtension.class)
class UnidadEstadoServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-20T15:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    @Mock
    private UnidadRepository unidadRepository;

    @InjectMocks
    private UnidadEstadoService service;

    private Logger logDelServicio;
    private ListAppender<ILoggingEvent> registros;

    @BeforeEach
    void capturarRegistros() {
        logDelServicio = (Logger) LoggerFactory.getLogger(UnidadEstadoService.class);
        registros = new ListAppender<>();
        registros.start();
        logDelServicio.addAppender(registros);
    }

    @AfterEach
    void soltarRegistros() {
        logDelServicio.detachAppender(registros);
    }

    private Unidad unidad(EstadoUnidad estado) {
        return Unidad.crear(TipoEquipo.crear("Multímetro", null, 7, false), "MUL-014", estado, null);
    }

    private boolean hayAdvertenciaCon(String texto) {
        return registros.list.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains(texto));
    }

    @Test
    @DisplayName("CA-1: una unidad disponible queda prestada")
    void quedaPrestada() {
        Unidad unidad = unidad(EstadoUnidad.DISPONIBLE);
        when(unidadRepository.findById(14L)).thenReturn(Optional.of(unidad));

        ResultadoEventoEstado resultado = service.aplicarCambioDeEstado(14L, EstadoUnidad.PRESTADA, T0);

        assertThat(resultado).isEqualTo(ResultadoEventoEstado.APLICADO);
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.PRESTADA);
    }

    @Test
    @DisplayName("CA-2: una unidad prestada queda disponible")
    void quedaDisponible() {
        Unidad unidad = unidad(EstadoUnidad.DISPONIBLE);
        unidad.aplicarEventoDeEstado(EstadoUnidad.PRESTADA, T0);
        when(unidadRepository.findById(14L)).thenReturn(Optional.of(unidad));

        ResultadoEventoEstado resultado = service.aplicarCambioDeEstado(14L, EstadoUnidad.DISPONIBLE, T1);

        assertThat(resultado).isEqualTo(ResultadoEventoEstado.APLICADO);
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.DISPONIBLE);
    }

    @Test
    @DisplayName("CA-4: el evento repetido no falla, no cambia nada y no es una advertencia")
    void eventoRepetido() {
        Unidad unidad = unidad(EstadoUnidad.DISPONIBLE);
        when(unidadRepository.findById(14L)).thenReturn(Optional.of(unidad));

        service.aplicarCambioDeEstado(14L, EstadoUnidad.PRESTADA, T0);
        ResultadoEventoEstado segunda = service.aplicarCambioDeEstado(14L, EstadoUnidad.PRESTADA, T0);

        assertThat(segunda).isEqualTo(ResultadoEventoEstado.SIN_CAMBIO);
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.PRESTADA);
        assertThat(registros.list).noneMatch(e -> e.getLevel() == Level.WARN);
    }

    @Test
    @DisplayName("CA-5: una unidad inexistente se descarta con un registro y sin excepción")
    void unidadInexistente() {
        when(unidadRepository.findById(999L)).thenReturn(Optional.empty());

        ResultadoEventoEstado resultado = service.aplicarCambioDeEstado(999L, EstadoUnidad.PRESTADA, T0);

        assertThat(resultado).isEqualTo(ResultadoEventoEstado.UNIDAD_INEXISTENTE);
        assertThat(hayAdvertenciaCon("999")).isTrue();
    }

    @Test
    @DisplayName("CA-9: un evento anterior se descarta con un registro y se conserva el estado más reciente")
    void fueraDeOrden() {
        Unidad unidad = unidad(EstadoUnidad.DISPONIBLE);
        unidad.aplicarEventoDeEstado(EstadoUnidad.PRESTADA, T1);
        when(unidadRepository.findById(14L)).thenReturn(Optional.of(unidad));

        ResultadoEventoEstado resultado = service.aplicarCambioDeEstado(14L, EstadoUnidad.DISPONIBLE, T0);

        assertThat(resultado).isEqualTo(ResultadoEventoEstado.FUERA_DE_ORDEN);
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.PRESTADA);
        assertThat(hayAdvertenciaCon("14")).isTrue();
    }

    @Test
    @DisplayName("CA-10: una unidad en revisión no pasa a prestada; se descarta con un registro")
    void unidadEnRevision() {
        Unidad unidad = unidad(EstadoUnidad.EN_REVISION);
        when(unidadRepository.findById(14L)).thenReturn(Optional.of(unidad));

        ResultadoEventoEstado resultado = service.aplicarCambioDeEstado(14L, EstadoUnidad.PRESTADA, T0);

        assertThat(resultado).isEqualTo(ResultadoEventoEstado.NO_APLICABLE);
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.EN_REVISION);
        assertThat(hayAdvertenciaCon("EN_REVISION")).isTrue();
    }

    @Test
    @DisplayName("un estado que el flujo no maneja se descarta con un registro")
    void estadoNoAdmitido() {
        Unidad unidad = unidad(EstadoUnidad.DISPONIBLE);
        when(unidadRepository.findById(14L)).thenReturn(Optional.of(unidad));

        ResultadoEventoEstado resultado = service.aplicarCambioDeEstado(14L, EstadoUnidad.EN_REVISION, T0);

        assertThat(resultado).isEqualTo(ResultadoEventoEstado.ESTADO_NO_ADMITIDO);
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.DISPONIBLE);
        assertThat(hayAdvertenciaCon("EN_REVISION")).isTrue();
    }
}
