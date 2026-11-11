package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.models.PrestamoRetrasado;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** SCRUM-23: dominio puro. La base está simulada y el reloj fijo: la regla se ejerce sin base de datos. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit tests — retrasos en PrestamoServiceImpl")
class RetrasosServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final String DIA_20 = "2026-11-20T15:00:00Z";

    @Mock
    private IPrestamoDao prestamoDao;

    @Mock
    private IInventarioClient inventarioClient;

    private PrestamoServiceImpl servicioConHoy(String instante) {
        Clock reloj = Clock.fixed(Instant.parse(instante), BOGOTA);
        return new PrestamoServiceImpl(prestamoDao, inventarioClient, new FechaServiceImpl(reloj));
    }

    private static Prestamo entregado(long id, long unidadId, String persona, LocalDate prevista) {
        Prestamo p = Prestamo.solicitar(unidadId, 2L, persona, 7, prevista.minusDays(7));
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "estado", EstadoPrestamo.ENTREGADO);
        return p;
    }

    private void entregadosEnBase(Prestamo... prestamos) {
        when(prestamoDao.findByEstado(EstadoPrestamo.ENTREGADO)).thenReturn(List.of(prestamos));
    }

    @Test
    @DisplayName("regla del retraso (CA-1) — dos vencidos y dos vigentes → exactamente los dos vencidos")
    void reglaRetraso_debeListarExactamenteLosVencidos() {
        entregadosEnBase(
                entregado(1L, 14L, "ana", LocalDate.of(2026, 11, 17)),
                entregado(2L, 15L, "luis", LocalDate.of(2026, 11, 25)),
                entregado(3L, 16L, "eva", LocalDate.of(2026, 11, 10)),
                entregado(4L, 17L, "leo", LocalDate.of(2026, 12, 1)));

        List<PrestamoRetrasado> retrasos = servicioConHoy(DIA_20).listarRetrasados();

        assertThat(retrasos).extracting(PrestamoRetrasado::prestamoId).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    @DisplayName("regla del retraso (CA-2) — devolución el 17 y hoy 20 → 3 días, con unidad, persona y fecha")
    void reglaRetraso_debeCalcularLosDiasDeRetraso() {
        entregadosEnBase(entregado(1L, 14L, "ana", LocalDate.of(2026, 11, 17)));

        assertThat(servicioConHoy(DIA_20).listarRetrasados()).containsExactly(
                new PrestamoRetrasado(1L, 14L, "ana", LocalDate.of(2026, 11, 17), 3));
    }

    @Test
    @DisplayName("regla del retraso (CA-3) — el que vence hoy no aparece")
    void reglaRetraso_elDiaDelVencimiento_noApareceEnElListado() {
        entregadosEnBase(entregado(1L, 14L, "ana", LocalDate.of(2026, 11, 20)));

        assertThat(servicioConHoy(DIA_20).listarRetrasados()).isEmpty();
    }

    @Test
    @DisplayName("regla del retraso (CA-5) — sin entregados → lista vacía, no error")
    void reglaRetraso_sinEntregados_debeDevolverListaVacia() {
        entregadosEnBase();

        assertThat(servicioConHoy(DIA_20).listarRetrasados()).isEmpty();
    }

    @Test
    @DisplayName("reloj inyectado (CA-6) — el mismo préstamo con dos relojes da días distintos")
    void relojInyectado_conOtroReloj_debeCambiarLosDias() {
        entregadosEnBase(entregado(1L, 14L, "ana", LocalDate.of(2026, 11, 17)));

        assertThat(servicioConHoy(DIA_20).listarRetrasados().get(0).diasRetraso()).isEqualTo(3);
        assertThat(servicioConHoy("2026-11-25T15:00:00Z").listarRetrasados().get(0).diasRetraso()).isEqualTo(8);
    }

    @Test
    @DisplayName("reloj inyectado (CA-6) — a las 22:00 en Bogotá ya son las 03:00 UTC del día siguiente: manda Bogotá")
    void relojInyectado_debeUsarLaZonaDelNegocio() {
        entregadosEnBase(entregado(1L, 14L, "ana", LocalDate.of(2026, 11, 20)));

        assertThat(servicioConHoy("2026-11-21T03:00:00Z").listarRetrasados()).isEmpty();
    }

    @Test
    @DisplayName("orden — el más atrasado primero, y a igual retraso por id")
    void orden_masAtrasadoPrimeroYDesempatePorId() {
        entregadosEnBase(
                entregado(5L, 14L, "ana", LocalDate.of(2026, 11, 17)),
                entregado(2L, 15L, "luis", LocalDate.of(2026, 11, 17)),
                entregado(9L, 16L, "eva", LocalDate.of(2026, 11, 1)));

        assertThat(servicioConHoy(DIA_20).listarRetrasados())
                .extracting(PrestamoRetrasado::prestamoId).containsExactly(9L, 2L, 5L);
    }

    @Test
    @DisplayName("independencia — listar retrasos no consulta a inventario")
    void listarRetrasados_noDebeConsultarInventario() {
        entregadosEnBase(entregado(1L, 14L, "ana", LocalDate.of(2026, 11, 17)));

        servicioConHoy(DIA_20).listarRetrasados();

        verifyNoInteractions(inventarioClient);
    }
}
