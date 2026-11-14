
package com.tedesoft.prestamosservice.domain.entities;

import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;
import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de Prestamo.
 * Incluye solicitud, entrega y devolución de unidades.
 */
@DisplayName("Unit tests — Prestamo")
class PrestamoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 2);

    // ==========================================
    // PRUEBAS EXISTENTES: SOLICITAR UN PRESTAMO
    // ==========================================

    @Test
    @DisplayName("solicitar — tipo de 7 días → fecha prevista 7 días después y duración guardada")
    void solicitar_tipoDeSieteDias_debeCalcularFechaYGuardarDuracion() {
        Prestamo prestamo = Prestamo.solicitar(14L, 2L, "ana", 7, HOY);

        assertThat(prestamo.getFechaSolicitud()).isEqualTo(HOY);
        assertThat(prestamo.getFechaPrevista())
                .isEqualTo(LocalDate.of(2026, 11, 9));
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
        assertThatThrownBy(() ->
                Prestamo.solicitar(14L, 2L, " ", 7, HOY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("solicitar — duración no positiva → IllegalArgumentException")
    void solicitar_duracionCero_debeLanzarExcepcion() {
        assertThatThrownBy(() ->
                Prestamo.solicitar(14L, 2L, "ana", 0, HOY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("solicitar — unidad o fecha nulas → NullPointerException")
    void solicitar_datosNulos_debeLanzarExcepcion() {
        assertThatThrownBy(() ->
                Prestamo.solicitar(null, 2L, "ana", 7, HOY))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() ->
                Prestamo.solicitar(14L, 2L, "ana", 7, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("solicitar — duración negativa → IllegalArgumentException")
    void solicitar_duracionNegativa_debeLanzarExcepcion() {
        assertThatThrownBy(() ->
                Prestamo.solicitar(14L, 2L, "ana", -1, HOY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("solicitar — tipo nulo → NullPointerException")
    void solicitar_tipoNulo_debeLanzarExcepcion() {
        assertThatThrownBy(() ->
                Prestamo.solicitar(14L, null, "ana", 7, HOY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("columna de estado — alcanza para PENDIENTE_AUTORIZACION")
    void columnaDeEstado_debeAlcanzarParaEstadosLargos()
            throws NoSuchFieldException {

        int largo = Prestamo.class.getDeclaredField("estado")
                .getAnnotation(Column.class)
                .length();

        assertThat(largo)
                .isGreaterThanOrEqualTo("PENDIENTE_AUTORIZACION".length());
    }

    @ParameterizedTest
    @EnumSource(
            value = EstadoPrestamo.class,
            names = {"SOLICITADO", "ENTREGADO"}
    )
    @DisplayName("estaActivo — solicitado y entregado ocupan cupo")
    void estaActivo_estadosAbiertos_debeSerTrue(EstadoPrestamo estado) {
        assertThat(estado.estaActivo()).isTrue();
    }

    @Test
    @DisplayName("estaActivo — devuelto libera el cupo")
    void estaActivo_devuelto_debeSerFalse() {
        assertThat(EstadoPrestamo.DEVUELTO.estaActivo()).isFalse();
    }

    // ==========================================
    // SCRUM-19: PRUEBAS DE ENTREGA
    // ==========================================

    @Test
    @DisplayName("entregar — cambia de SOLICITADO a ENTREGADO")
    void entregar_prestamoSolicitado_debeCambiarAEntregado() {
        Prestamo prestamo = Prestamo.solicitar(
                14L, 2L, "ana", 7, HOY
        );

        assertThat(prestamo.getEstado())
                .isEqualTo(EstadoPrestamo.SOLICITADO);

        prestamo.entregar();

        assertThat(prestamo.getEstado())
                .isEqualTo(EstadoPrestamo.ENTREGADO);
    }

    @Test
    @DisplayName("entregar — impide entregar dos veces")
    void entregar_prestamoYaEntregado_debeLanzarExcepcion() {
        Prestamo prestamo = Prestamo.solicitar(
                14L, 2L, "ana", 7, HOY
        );

        prestamo.entregar();

        assertThatThrownBy(prestamo::entregar)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Estado actual: ENTREGADO");

        assertThat(prestamo.getEstado())
                .isEqualTo(EstadoPrestamo.ENTREGADO);
    }

    // ==========================================
    // SCRUM-19: PRUEBAS DE DEVOLUCION
    // ==========================================

    @Test
    @DisplayName("recibir — cambia de ENTREGADO a DEVUELTO")
    void recibir_prestamoEntregado_debeCambiarADevuelto() {
        Prestamo prestamo = Prestamo.solicitar(
                14L, 2L, "ana", 7, HOY
        );

        prestamo.entregar();
        prestamo.recibir();

        assertThat(prestamo.getEstado())
                .isEqualTo(EstadoPrestamo.DEVUELTO);
    }

    @Test
    @DisplayName("recibir — no permite devolver un préstamo solicitado")
    void recibir_prestamoSolicitado_debeLanzarExcepcion() {
        Prestamo prestamo = Prestamo.solicitar(
                14L, 2L, "ana", 7, HOY
        );

        assertThatThrownBy(prestamo::recibir)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Estado actual: SOLICITADO");

        assertThat(prestamo.getEstado())
                .isEqualTo(EstadoPrestamo.SOLICITADO);
    }

    @Test
    @DisplayName("recibir — impide devolver dos veces")
    void recibir_prestamoYaDevuelto_debeLanzarExcepcion() {
        Prestamo prestamo = Prestamo.solicitar(
                14L, 2L, "ana", 7, HOY
        );

        prestamo.entregar();
        prestamo.recibir();

        assertThatThrownBy(prestamo::recibir)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Estado actual: DEVUELTO");

        assertThat(prestamo.getEstado())
                .isEqualTo(EstadoPrestamo.DEVUELTO);
    }

    // ==========================================
    // SCRUM-19: FLUJO COMPLETO
    // ==========================================

    @Test
    @DisplayName("entregar — no permite entregar un préstamo devuelto")
    void entregar_prestamoDevuelto_debeLanzarExcepcion() {
        Prestamo prestamo = Prestamo.solicitar(
                14L, 2L, "ana", 7, HOY
        );

        prestamo.entregar();
        prestamo.recibir();

        assertThatThrownBy(prestamo::entregar)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Estado actual: DEVUELTO");

        assertThat(prestamo.getEstado())
                .isEqualTo(EstadoPrestamo.DEVUELTO);
    }
}
