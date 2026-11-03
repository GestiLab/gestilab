package com.tedesoft.inventarioservice.domain.entities;

import com.tedesoft.inventarioservice.domain.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de dominio puras: sin Spring, sin base de datos y sin reloj del sistema.
 * La fecha se inyecta siempre como parámetro, por lo que el resultado no depende del día de ejecución.
 */
class TipoEquipoTest {

    private static final LocalDate FECHA_BASE = LocalDate.of(2026, 10, 30);

    @Test
    @DisplayName("crear conserva exactamente los datos recibidos")
    void crearConservaLosDatos() {
        TipoEquipo tipo = TipoEquipo.crear("Multímetro", "Mide voltaje y corriente", 7, false);

        assertThat(tipo.getNombre()).isEqualTo("Multímetro");
        assertThat(tipo.getDescripcion()).isEqualTo("Mide voltaje y corriente");
        assertThat(tipo.getDuracionMaximaDias()).isEqualTo(7);
        assertThat(tipo.isRequiereAutorizacion()).isFalse();
    }

    @Test
    @DisplayName("un tipo con autorización lo refleja")
    void tipoConAutorizacion() {
        TipoEquipo camara = TipoEquipo.crear("Cámara", "Cámara fotográfica", 3, true);

        assertThat(camara.isRequiereAutorizacion()).isTrue();
    }

    @Test
    @DisplayName("editar reglas cambia la duración y la fecha de devolución que dicta el tipo")
    void editarReglasCambiaLaDuracion() {
        TipoEquipo tipo = TipoEquipo.crear("Multímetro", "desc", 7, false);

        tipo.actualizar("Multímetro", "desc", 3, false);

        assertThat(tipo.getDuracionMaximaDias()).isEqualTo(3);
        assertThat(tipo.calcularFechaDevolucion(FECHA_BASE)).isEqualTo(FECHA_BASE.plusDays(3));
    }

    @ParameterizedTest(name = "{0} con {1} días devuelve el {2}")
    @CsvSource({
            "Multímetro, 7, 2026-11-06",
            "Portátil,   1, 2026-10-31",
            "Cámara,     3, 2026-11-02",
            "Osciloscopio, 14, 2026-11-13"
    })
    @DisplayName("cada tipo dicta su fecha con el mismo código; solo cambian los datos")
    void cadaTipoDictaSuFecha(String nombre, int dias, LocalDate esperada) {
        TipoEquipo tipo = TipoEquipo.crear(nombre, null, dias, false);

        assertThat(tipo.calcularFechaDevolucion(FECHA_BASE)).isEqualTo(esperada);
    }

    @Test
    @DisplayName("la fecha de devolución cruza fin de mes y año bisiesto correctamente")
    void fechaDevolucionCruzaFinDeMesBisiesto() {
        TipoEquipo tipo = TipoEquipo.crear("Multímetro", null, 7, false);

        assertThat(tipo.calcularFechaDevolucion(LocalDate.of(2028, 2, 25)))
                .isEqualTo(LocalDate.of(2028, 3, 3));
    }

    @Test
    @DisplayName("calcular la devolución sin fecha de inicio es un error de programación")
    void fechaInicioNulaLanzaExcepcion() {
        TipoEquipo tipo = TipoEquipo.crear("Multímetro", null, 7, false);

        assertThatThrownBy(() -> tipo.calcularFechaDevolucion(null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "    "})
    @DisplayName("el nombre nulo, vacío o en blanco se rechaza")
    void nombreInvalido(String nombre) {
        assertThatThrownBy(() -> TipoEquipo.crear(nombre, "desc", 7, false))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("nombre");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -30})
    @DisplayName("la duración máxima cero o negativa se rechaza")
    void duracionInvalida(int dias) {
        assertThatThrownBy(() -> TipoEquipo.crear("Multímetro", "desc", dias, false))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("duración");
    }

    @Test
    @DisplayName("una edición inválida no altera el estado del tipo")
    void edicionInvalidaNoAlteraElEstado() {
        TipoEquipo tipo = TipoEquipo.crear("Multímetro", "desc", 7, false);

        assertThatThrownBy(() -> tipo.actualizar("Otro nombre", "otra desc", 0, true))
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(tipo.getNombre()).isEqualTo("Multímetro");
        assertThat(tipo.getDescripcion()).isEqualTo("desc");
        assertThat(tipo.getDuracionMaximaDias()).isEqualTo(7);
        assertThat(tipo.isRequiereAutorizacion()).isFalse();
    }
}
