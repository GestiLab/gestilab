package com.tedesoft.inventarioservice.domain.repositories;

import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Dominio puro: el escape de comodines, sin base de datos. La consulta real se prueba en la integración. */
class UnidadSpecificationTest {

    @Test
    @DisplayName("CA-9: % y _ se escapan para buscarse como texto literal")
    void escapaPorcientoYGuionBajo() {
        assertThat(UnidadSpecification.escapar("50%")).isEqualTo("50\\%");
        assertThat(UnidadSpecification.escapar("sensor_a")).isEqualTo("sensor\\_a");
        assertThat(UnidadSpecification.escapar("%_")).isEqualTo("\\%\\_");
    }

    @Test
    @DisplayName("CA-9: la barra de escape también se escapa, y primero, para no duplicar el escape")
    void escapaLaBarra() {
        assertThat(UnidadSpecification.escapar("a\\b")).isEqualTo("a\\\\b");
        assertThat(UnidadSpecification.escapar("\\%")).isEqualTo("\\\\\\%");
    }

    @Test
    @DisplayName("un texto sin comodines no cambia")
    void textoNormalNoCambia() {
        assertThat(UnidadSpecification.escapar("multímetro 7")).isEqualTo("multímetro 7");
    }

    @Test
    @DisplayName("combinar filtros ausentes produce una especificación válida (sin restricciones)")
    void sinFiltros() {
        assertThat(UnidadSpecification.filtrar(null, null, null)).isNotNull();
        assertThat(UnidadSpecification.filtrar(1L, EstadoUnidad.DISPONIBLE, "mult")).isNotNull();
    }
}
