package com.tedesoft.prestamosservice.domain.models;

import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** SCRUM-23 y RF-08: la regla del retraso, sin base de datos y sin reloj del sistema. */
@DisplayName("Unit tests — regla del retraso en PrestamoRetrasado")
class PrestamoRetrasadoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 20);

    private static Prestamo prestamo(EstadoPrestamo estado, LocalDate prevista) {
        Prestamo p = Prestamo.solicitar(14L, 2L, "ana", 7, prevista.minusDays(7));
        ReflectionTestUtils.setField(p, "id", 1L);
        ReflectionTestUtils.setField(p, "estado", estado);
        return p;
    }

    @Test
    @DisplayName("regla del retraso — entregado con la fecha ya pasada está retrasado")
    void reglaRetraso_entregadoVencido_estaRetrasado() {
        assertThat(PrestamoRetrasado.aplicaA(prestamo(EstadoPrestamo.ENTREGADO, HOY.minusDays(1)), HOY)).isTrue();
    }

    @Test
    @DisplayName("regla del retraso — el día del vencimiento todavía no es retraso (CA-3)")
    void reglaRetraso_elDiaDelVencimiento_noEstaRetrasado() {
        assertThat(PrestamoRetrasado.aplicaA(prestamo(EstadoPrestamo.ENTREGADO, HOY), HOY)).isFalse();
    }

    @Test
    @DisplayName("regla del retraso — entregado con la fecha por venir no está retrasado (CA-1)")
    void reglaRetraso_entregadoVigente_noEstaRetrasado() {
        assertThat(PrestamoRetrasado.aplicaA(prestamo(EstadoPrestamo.ENTREGADO, HOY.plusDays(1)), HOY)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPrestamo.class, mode = EnumSource.Mode.EXCLUDE, names = "ENTREGADO")
    @DisplayName("regla del retraso — solo un préstamo entregado puede estar retrasado (CA-4)")
    void reglaRetraso_otroEstadoConFechaPasada_noEstaRetrasado(EstadoPrestamo estado) {
        assertThat(PrestamoRetrasado.aplicaA(prestamo(estado, HOY.minusDays(30)), HOY)).isFalse();
    }

    @Test
    @DisplayName("desde — copia los datos y calcula los días contra la fecha recibida (CA-2)")
    void desde_debeCopiarDatosYCalcularDias() {
        PrestamoRetrasado retraso = PrestamoRetrasado.desde(
                prestamo(EstadoPrestamo.ENTREGADO, LocalDate.of(2026, 11, 17)), HOY);

        assertThat(retraso).isEqualTo(new PrestamoRetrasado(1L, 14L, "ana", LocalDate.of(2026, 11, 17), 3));
    }

    @Test
    @DisplayName("desde — cuenta bien a través de un fin de año bisiesto")
    void desde_atravesDeUnAnioBisiesto_debeContarLosDiasReales() {
        PrestamoRetrasado retraso = PrestamoRetrasado.desde(
                prestamo(EstadoPrestamo.ENTREGADO, LocalDate.of(2028, 2, 28)), LocalDate.of(2028, 3, 1));

        assertThat(retraso.diasRetraso()).isEqualTo(2);
    }
}
