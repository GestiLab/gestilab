
package com.tedesoft.prestamosservice.domain.entities;

import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de las reglas de dominio para los préstamos
 * que requieren autorización previa.
 */
@DisplayName("Pruebas de autorización de préstamos")
class PrestamoAutorizacionTest {

    private static final LocalDate HOY =
            LocalDate.of(2026, 11, 27);

    // ==========================================
    // ESTADO INICIAL DE LA SOLICITUD
    // ==========================================

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("El estado inicial depende de si requiere autorización")
    void solicitarSegunRequiereAutorizacion(
            boolean requiereAutorizacion) {

        Prestamo prestamo = Prestamo.solicitar(
                14L, 2L, "ana", 7, HOY,
                requiereAutorizacion
        );

        EstadoPrestamo esperado = requiereAutorizacion
                ? EstadoPrestamo.PENDIENTE_AUTORIZACION
                : EstadoPrestamo.SOLICITADO;

        assertThat(prestamo.getEstado())
                .isEqualTo(esperado);

        assertThat(prestamo.getFechaSolicitud())
                .isEqualTo(HOY);

        assertThat(prestamo.getFechaPrevista())
                .isEqualTo(HOY.plusDays(7));

        assertThat(prestamo.getDuracionAplicadaDias())
                .isEqualTo(7);
    }

    // ==========================================
    // AUTORIZACION DEL PRESTAMO
    // ==========================================

    @Test
    @DisplayName("Autorizar cambia el estado y conserva las fechas")
    void autorizarPrestamoPendiente() {

        Prestamo prestamo = Prestamo.solicitar(
                14L, 2L, "ana", 7, HOY, true
        );

        prestamo.autorizar();

        assertThat(prestamo.getEstado())
                .isEqualTo(EstadoPrestamo.SOLICITADO);

        assertThat(prestamo.getFechaSolicitud())
                .isEqualTo(HOY);

        assertThat(prestamo.getFechaPrevista())
                .isEqualTo(HOY.plusDays(7));

        assertThat(prestamo.getDuracionAplicadaDias())
                .isEqualTo(7);
    }

    @ParameterizedTest
    @EnumSource(
            value = EstadoPrestamo.class,
            names = {
                    "SOLICITADO",
                    "ENTREGADO",
                    "DEVUELTO"
            }
    )
    @DisplayName("Solo se pueden autorizar préstamos pendientes")
    void autorizarEstadoIncorrecto(EstadoPrestamo estado) {

        Prestamo prestamo = Prestamo.solicitar(
                14L, 2L, "ana", 7, HOY, false
        );

        if (estado == EstadoPrestamo.ENTREGADO
                || estado == EstadoPrestamo.DEVUELTO) {
            prestamo.entregar();
        }

        if (estado == EstadoPrestamo.DEVUELTO) {
            prestamo.recibir();
        }

        assertThatThrownBy(prestamo::autorizar)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining(estado.name());

        assertThat(prestamo.getEstado())
                .isEqualTo(estado);
    }

    // ==========================================
    // IMPEDIR LA ENTREGA SIN AUTORIZACION
    // ==========================================

    @Test
    @DisplayName("No se puede entregar un préstamo pendiente")
    void entregarSinAutorizacion() {

        Prestamo prestamo = Prestamo.solicitar(
                14L, 2L, "ana", 7, HOY, true
        );

        assertThatThrownBy(prestamo::entregar)
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(error ->
                        assertThat(error.getMessage())
                                .containsIgnoringCase("autoriz")
                );

        assertThat(prestamo.getEstado())
                .isEqualTo(
                        EstadoPrestamo.PENDIENTE_AUTORIZACION
                );
    }

    // ==========================================
    // ESTADOS ACTIVOS
    // ==========================================

    @Test
    @DisplayName("Un préstamo pendiente ocupa cupo")
    void pendienteEsUnEstadoActivo() {

        assertThat(
                EstadoPrestamo.PENDIENTE_AUTORIZACION
                        .estaActivo()
        ).isTrue();

        assertThat(EstadoPrestamo.activos())
                .contains(
                        EstadoPrestamo.PENDIENTE_AUTORIZACION,
                        EstadoPrestamo.SOLICITADO,
                        EstadoPrestamo.ENTREGADO
                )
                .doesNotContain(EstadoPrestamo.DEVUELTO);
    }
}
