
package com.tedesoft.prestamosservice.infrastructure.persistence;

import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Índice único de préstamos abiertos: el SQL sale de los estados activos")
class IndiceUnicoPrestamoAbiertoTest {

    @Test
    @DisplayName("incluye todos los estados activos y deja fuera los cerrados")
    void incluyeSoloLosActivos() {
        String sql = IndiceUnicoPrestamoAbierto.sql();

        for (EstadoPrestamo estado : EstadoPrestamo.values()) {
            if (estado.estaActivo()) {
                assertThat(sql).contains("'" + estado.name() + "'");
            } else {
                assertThat(sql).doesNotContain(estado.name());
            }
        }
        assertThat(sql).startsWith("CREATE UNIQUE INDEX " + IndiceUnicoPrestamoAbierto.NOMBRE)
                .contains("ON prestamo (unidad_id) WHERE estado IN (");
    }

    @Test
    @DisplayName("se recrea siempre: primero se elimina el índice anterior")
    void seEliminaAntesDeCrear() {
        assertThat(IndiceUnicoPrestamoAbierto.sqlEliminar())
                .isEqualTo("DROP INDEX IF EXISTS " + IndiceUnicoPrestamoAbierto.NOMBRE);
    }

    @Test
    @DisplayName("el CHECK de PostgreSQL admite todos los estados del préstamo")
    void checkIncluyeTodosLosEstados() {
        String sql = IndiceUnicoPrestamoAbierto.sqlActualizarCheck();

        for (EstadoPrestamo estado : EstadoPrestamo.values()) {
            assertThat(sql).contains("'" + estado.name() + "'");
        }

        assertThat(sql)
                .contains("ALTER TABLE prestamo")
                .contains("DROP CONSTRAINT IF EXISTS prestamo_estado_check")
                .contains("ADD CONSTRAINT prestamo_estado_check")
                .contains("CHECK (estado IN (");
    }

    @Test
    @DisplayName("un préstamo pendiente de autorización reserva la unidad")
    void pendienteDeAutorizacionReservaUnidad() {
        assertThat(EstadoPrestamo.PENDIENTE_AUTORIZACION.estaActivo())
                .isTrue();

        assertThat(IndiceUnicoPrestamoAbierto.sql())
                .contains("'PENDIENTE_AUTORIZACION'")
                .doesNotContain("'DEVUELTO'");
    }
}
