package com.tedesoft.inventarioservice.domain.entities;

import com.tedesoft.inventarioservice.domain.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dominio puro: la restricción de estados del enum, sin Spring ni base de datos. */
class EstadoUnidadTest {

    @Test
    @DisplayName("los únicos estados son disponible, prestada, en revisión y fuera de servicio")
    void soloCuatroEstados() {
        assertThat(EstadoUnidad.values())
                .containsExactly(EstadoUnidad.DISPONIBLE, EstadoUnidad.PRESTADA,
                        EstadoUnidad.EN_REVISION, EstadoUnidad.FUERA_DE_SERVICIO);
    }

    @ParameterizedTest(name = "\"{0}\" se convierte en {1}")
    @CsvSource({
            "DISPONIBLE, DISPONIBLE",
            "disponible, DISPONIBLE",
            "'  Prestada ', PRESTADA",
            "EN_REVISION, EN_REVISION",
            "en_revision, EN_REVISION",
            "FUERA_DE_SERVICIO, FUERA_DE_SERVICIO"
    })
    void textoValidoSeConvierte(String texto, EstadoUnidad esperado) {
        assertThat(EstadoUnidad.esValido(texto)).isTrue();
        assertThat(EstadoUnidad.desdeTexto(texto)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"perdida", "en revisión", "EN REVISION", "   ", "DISPONIBLES"})
    @DisplayName("un estado no permitido se rechaza y el mensaje lista los permitidos")
    void textoInvalidoSeRechaza(String texto) {
        assertThat(EstadoUnidad.esValido(texto)).isFalse();
        assertThatThrownBy(() -> EstadoUnidad.desdeTexto(texto))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("DISPONIBLE")
                .hasMessageContaining("PRESTADA")
                .hasMessageContaining("EN_REVISION")
                .hasMessageContaining("FUERA_DE_SERVICIO");
    }

    @Test
    @DisplayName("solo una unidad disponible se puede prestar")
    void soloDisponibleSePuedePrestar() {
        assertThat(EstadoUnidad.DISPONIBLE.sePuedePrestar()).isTrue();
        assertThat(EstadoUnidad.PRESTADA.sePuedePrestar()).isFalse();
        assertThat(EstadoUnidad.EN_REVISION.sePuedePrestar()).isFalse();
        assertThat(EstadoUnidad.FUERA_DE_SERVICIO.sePuedePrestar()).isFalse();
    }

    @Test
    void permitidosComoTextoListaLosCuatro() {
        assertThat(EstadoUnidad.permitidosComoTexto())
                .isEqualTo("DISPONIBLE, PRESTADA, EN_REVISION, FUERA_DE_SERVICIO");
    }
}
