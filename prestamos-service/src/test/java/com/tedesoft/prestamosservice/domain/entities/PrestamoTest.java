package com.tedesoft.prestamosservice.domain.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JUnit puro: la fecha de hoy se pasa a mano, sin reloj ni Spring. */
@DisplayName("Unit tests — Prestamo")
class PrestamoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 2);

    @Test
    @DisplayName("solicitar — tipo de 7 días → fecha prevista 7 días después y duración guardada")
    void solicitar_tipoDeSieteDias_debeCalcularFechaYGuardarDuracion() {
        Prestamo prestamo = Prestamo.solicitar(14L, 2L, "ana", 7, HOY);

        assertThat(prestamo.getFechaSolicitud()).isEqualTo(HOY);
        assertThat(prestamo.getFechaPrevista()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(prestamo.getDuracionAplicadaDias()).isEqualTo(7);
        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.SOLICITADO);
    }

    @Test
    @DisplayName("solicitar — recorta el identificador del solicitante")
    void solicitar_solicitanteConEspacios_debeRecortarlo() {
        Prestamo prestamo = Prestamo.solicitar(14L, 2L, "  ana  ", 7, HOY);

        assertThat(prestamo.getSolicitanteId()).isEqualTo("ana");
    }

    @Test
    @DisplayName("solicitar — solicitante vacío → IllegalArgumentException")
    void solicitar_solicitanteVacio_debeLanzarExcepcion() {
        assertThatThrownBy(() -> Prestamo.solicitar(14L, 2L, " ", 7, HOY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("solicitar — duración no positiva → IllegalArgumentException")
    void solicitar_duracionCero_debeLanzarExcepcion() {
        assertThatThrownBy(() -> Prestamo.solicitar(14L, 2L, "ana", 0, HOY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("solicitar — unidad o fecha nulas → NullPointerException")
    void solicitar_datosNulos_debeLanzarExcepcion() {
        assertThatThrownBy(() -> Prestamo.solicitar(null, 2L, "ana", 7, HOY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Prestamo.solicitar(14L, 2L, "ana", 7, null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPrestamo.class, names = {"SOLICITADO", "ENTREGADO"})
    @DisplayName("estaActivo — solicitado y entregado ocupan cupo")
    void estaActivo_estadosAbiertos_debeSerTrue(EstadoPrestamo estado) {
        assertThat(estado.estaActivo()).isTrue();
    }

    @Test
    @DisplayName("estaActivo — devuelto libera el cupo")
    void estaActivo_devuelto_debeSerFalse() {
        assertThat(EstadoPrestamo.DEVUELTO.estaActivo()).isFalse();
    }
}
