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
}
