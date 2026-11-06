package com.tedesoft.inventarioservice.domain.entities;

import com.tedesoft.inventarioservice.domain.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dominio puro: reglas de la unidad, sin Spring, sin base de datos y sin reloj. */
class UnidadTest {

    private final TipoEquipo multimetro = TipoEquipo.crear("Multímetro", "Mide voltaje", 7, false);

    @Test
    @DisplayName("CA-1: crear conserva número, estado, observaciones y tipo")
    void crearConservaLosDatos() {
        Unidad unidad = Unidad.crear(multimetro, "MUL-014", EstadoUnidad.DISPONIBLE, "Buen estado");

        assertThat(unidad.getNumeroInventario()).isEqualTo("MUL-014");
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.DISPONIBLE);
        assertThat(unidad.getObservaciones()).isEqualTo("Buen estado");
        assertThat(unidad.getTipo()).isSameAs(multimetro);
    }

    @Test
    @DisplayName("el número y las observaciones se guardan sin espacios sobrantes; las observaciones son opcionales")
    void normalizaEspacios() {
        Unidad unidad = Unidad.crear(multimetro, "  MUL-014 ", EstadoUnidad.DISPONIBLE, "  ok  ");
        Unidad sinObservaciones = Unidad.crear(multimetro, "MUL-015", EstadoUnidad.DISPONIBLE, null);

        assertThat(unidad.getNumeroInventario()).isEqualTo("MUL-014");
        assertThat(unidad.getObservaciones()).isEqualTo("ok");
        assertThat(sinObservaciones.getObservaciones()).isNull();
    }

    @Test
    @DisplayName("el número de inventario se guarda en mayúsculas: mul-014 y MUL-014 son la misma unidad")
    void numeroSeGuardaEnMayusculas() {
        Unidad creada = Unidad.crear(multimetro, " mul-014 ", EstadoUnidad.DISPONIBLE, null);
        assertThat(creada.getNumeroInventario()).isEqualTo("MUL-014");

        creada.actualizar(multimetro, "cam-002", EstadoUnidad.DISPONIBLE, null);
        assertThat(creada.getNumeroInventario()).isEqualTo("CAM-002");
    }

    @Test
    @DisplayName("CA-3: editar al estado EN_REVISION lo refleja y deja de poder prestarse")
    void editarAEnRevision() {
        Unidad unidad = Unidad.crear(multimetro, "MUL-014", EstadoUnidad.DISPONIBLE, null);
        assertThat(unidad.sePuedePrestar()).isTrue();

        unidad.actualizar(multimetro, "MUL-014", EstadoUnidad.EN_REVISION, "Pendiente de calibración");

        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.EN_REVISION);
        assertThat(unidad.getObservaciones()).isEqualTo("Pendiente de calibración");
        assertThat(unidad.sePuedePrestar()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("el número de inventario nulo, vacío o en blanco se rechaza")
    void numeroInvalido(String numero) {
        assertThatThrownBy(() -> Unidad.crear(multimetro, numero, EstadoUnidad.DISPONIBLE, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("número de inventario");
    }

    @Test
    @DisplayName("el estado es obligatorio")
    void estadoObligatorio() {
        assertThatThrownBy(() -> Unidad.crear(multimetro, "MUL-014", null, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("estado");
    }

    @Test
    @DisplayName("la unidad debe pertenecer a un tipo de equipo")
    void tipoObligatorio() {
        assertThatThrownBy(() -> Unidad.crear(null, "MUL-014", EstadoUnidad.DISPONIBLE, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("tipo de equipo");
    }

    @Test
    @DisplayName("una edición inválida no altera el estado de la unidad")
    void edicionInvalidaNoAltera() {
        Unidad unidad = Unidad.crear(multimetro, "MUL-014", EstadoUnidad.DISPONIBLE, "ok");
        TipoEquipo otro = TipoEquipo.crear("Cámara", null, 3, true);

        assertThatThrownBy(() -> unidad.actualizar(otro, "  ", EstadoUnidad.FUERA_DE_SERVICIO, "x"))
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(unidad.getTipo()).isSameAs(multimetro);
        assertThat(unidad.getNumeroInventario()).isEqualTo("MUL-014");
        assertThat(unidad.getEstado()).isEqualTo(EstadoUnidad.DISPONIBLE);
        assertThat(unidad.getObservaciones()).isEqualTo("ok");
    }
}
