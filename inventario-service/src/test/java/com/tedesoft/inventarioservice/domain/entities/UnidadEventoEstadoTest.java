package com.tedesoft.inventarioservice.domain.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dominio puro: las fechas llegan dentro del evento, sin reloj del sistema, base de datos ni broker. */
class UnidadEventoEstadoTest {

    private static final Instant T0 = Instant.parse("2026-11-20T15:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    private final TipoEquipo tipo = TipoEquipo.crear("Multímetro", null, 7, false);

    private Unidad unidad(EstadoUnidad estado) {
        return Unidad.crear(tipo, "MUL-014", estado, null);
    }

    @Test
    @DisplayName("CA-1: una unidad disponible pasa a prestada")
    void disponiblePasaAPrestada() {
        Unidad unidad = unidad(EstadoUnidad.DISPONIBLE);

        ResultadoEventoEstado resultado = unidad.aplicarEventoDeEstado(EstadoUnidad.PRESTADA, T0);

        assertThat(resultado).isEqualTo(ResultadoEventoEstado.APLICADO);
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.PRESTADA);
        assertThat(unidad.getUltimoEventoEn()).isEqualTo(T0);
    }

    @Test
    @DisplayName("CA-2: una unidad prestada pasa a disponible")
    void prestadaPasaADisponible() {
        Unidad unidad = unidad(EstadoUnidad.DISPONIBLE);
        unidad.aplicarEventoDeEstado(EstadoUnidad.PRESTADA, T0);

        ResultadoEventoEstado resultado = unidad.aplicarEventoDeEstado(EstadoUnidad.DISPONIBLE, T1);

        assertThat(resultado).isEqualTo(ResultadoEventoEstado.APLICADO);
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.DISPONIBLE);
        assertThat(unidad.getUltimoEventoEn()).isEqualTo(T1);
    }

    @Test
    @DisplayName("CA-4: el mismo evento dos veces deja el mismo resultado y no falla")
    void eventoRepetido() {
        Unidad unidad = unidad(EstadoUnidad.DISPONIBLE);
        unidad.aplicarEventoDeEstado(EstadoUnidad.PRESTADA, T0);

        ResultadoEventoEstado segunda = unidad.aplicarEventoDeEstado(EstadoUnidad.PRESTADA, T0);

        assertThat(segunda).isEqualTo(ResultadoEventoEstado.SIN_CAMBIO);
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.PRESTADA);
        assertThat(unidad.getUltimoEventoEn()).isEqualTo(T0);
    }

    @Test
    @DisplayName("CA-4: el mismo estado con fecha posterior no cambia nada pero adelanta la marca")
    void mismoEstadoConFechaPosterior() {
        Unidad unidad = unidad(EstadoUnidad.DISPONIBLE);

        ResultadoEventoEstado resultado = unidad.aplicarEventoDeEstado(EstadoUnidad.DISPONIBLE, T1);

        assertThat(resultado).isEqualTo(ResultadoEventoEstado.SIN_CAMBIO);
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.DISPONIBLE);
        assertThat(unidad.getUltimoEventoEn()).isEqualTo(T1);
    }

    @Test
    @DisplayName("CA-9: un evento anterior al último aplicado se descarta y se conserva el estado más reciente")
    void eventoFueraDeOrden() {
        Unidad unidad = unidad(EstadoUnidad.DISPONIBLE);
        unidad.aplicarEventoDeEstado(EstadoUnidad.PRESTADA, T1);

        ResultadoEventoEstado resultado = unidad.aplicarEventoDeEstado(EstadoUnidad.DISPONIBLE, T0);

        assertThat(resultado).isEqualTo(ResultadoEventoEstado.FUERA_DE_ORDEN);
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.PRESTADA);
        assertThat(unidad.getUltimoEventoEn()).isEqualTo(T1);
    }

    @Test
    @DisplayName("CA-9: dos eventos de la misma fecha con estados distintos: se conserva el ya aplicado")
    void mismaFechaOtroEstado() {
        Unidad unidad = unidad(EstadoUnidad.DISPONIBLE);
        unidad.aplicarEventoDeEstado(EstadoUnidad.PRESTADA, T0);

        ResultadoEventoEstado resultado = unidad.aplicarEventoDeEstado(EstadoUnidad.DISPONIBLE, T0);

        assertThat(resultado).isEqualTo(ResultadoEventoEstado.FUERA_DE_ORDEN);
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.PRESTADA);
    }

    @ParameterizedTest(name = "unidad {0} + evento {1}")
    @CsvSource({
            "EN_REVISION, PRESTADA",
            "FUERA_DE_SERVICIO, PRESTADA",
            "EN_REVISION, DISPONIBLE",
            "FUERA_DE_SERVICIO, DISPONIBLE"
    })
    @DisplayName("CA-10: una unidad en revisión o fuera de servicio no cambia con el evento")
    void unidadNoPrestable(EstadoUnidad actual, EstadoUnidad evento) {
        Unidad unidad = unidad(actual);

        ResultadoEventoEstado resultado = unidad.aplicarEventoDeEstado(evento, T0);

        assertThat(resultado).isEqualTo(ResultadoEventoEstado.NO_APLICABLE);
        assertThat(unidad.getEstado()).isEqualTo(actual);
        assertThat(unidad.getUltimoEventoEn()).isNull();
    }

    @ParameterizedTest(name = "estado del evento {0}")
    @EnumSource(value = EstadoUnidad.class, names = {"EN_REVISION", "FUERA_DE_SERVICIO"})
    @DisplayName("un evento con un estado distinto de PRESTADA o DISPONIBLE no se admite")
    void estadoNoAdmitido(EstadoUnidad estadoDelEvento) {
        Unidad unidad = unidad(EstadoUnidad.DISPONIBLE);

        ResultadoEventoEstado resultado = unidad.aplicarEventoDeEstado(estadoDelEvento, T0);

        assertThat(resultado).isEqualTo(ResultadoEventoEstado.ESTADO_NO_ADMITIDO);
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.DISPONIBLE);
        assertThat(unidad.getUltimoEventoEn()).isNull();
    }

    @Test
    @DisplayName("el estado y la fecha del evento son obligatorios")
    void datosObligatorios() {
        Unidad unidad = unidad(EstadoUnidad.DISPONIBLE);

        assertThatThrownBy(() -> unidad.aplicarEventoDeEstado(null, T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> unidad.aplicarEventoDeEstado(EstadoUnidad.PRESTADA, null))
                .isInstanceOf(NullPointerException.class);
    }
}
