package com.tedesoft.inventarioservice.domain.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** CA-4 y CA-9: la búsqueda por nombre del tipo, en el dominio y sin base de datos. */
class TipoEquipoBusquedaTest {

    private final TipoEquipo multimetro = TipoEquipo.crear("Multímetro", null, 7, false);

    @ParameterizedTest(name = "\"{0}\" coincide con Multímetro")
    @ValueSource(strings = {"multi", "MULTI", "multimetro", "MULTÍMETRO", "multí", "metro", "  mult  ", " mult "})
    @DisplayName("CA-4: parcial, sin distinguir mayúsculas ni tildes, ignorando espacios laterales")
    void coincide(String busqueda) {
        assertThat(multimetro.nombreContiene(busqueda)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" no coincide con Multímetro")
    @ValueSource(strings = {"portatil", "multimetros", "m%o", "m_ltimetro"})
    @DisplayName("CA-4: un texto que no está en el nombre no coincide")
    void noCoincide(String busqueda) {
        assertThat(multimetro.nombreContiene(busqueda)).isFalse();
    }

    @Test
    @DisplayName("CA-9: % y _ se comparan como texto literal, no como comodines")
    void comodinesComoTexto() {
        assertThat(TipoEquipo.crear("Descuento 50% nuevo", null, 2, false).nombreContiene("50%")).isTrue();
        assertThat(TipoEquipo.crear("Descuento 50 nuevo", null, 2, false).nombreContiene("50%")).isFalse();
        assertThat(TipoEquipo.crear("Sensor_A", null, 2, false).nombreContiene("sensor_")).isTrue();
        assertThat(TipoEquipo.crear("SensorXA", null, 2, false).nombreContiene("sensor_")).isFalse();
    }

    @Test
    @DisplayName("una búsqueda nula o en blanco no restringe: coincide con cualquier tipo")
    void vaciaCoincide() {
        assertThat(multimetro.nombreContiene(null)).isTrue();
        assertThat(multimetro.nombreContiene("   ")).isTrue();
    }
}
