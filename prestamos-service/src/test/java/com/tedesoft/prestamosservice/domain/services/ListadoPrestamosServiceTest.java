package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;
import com.tedesoft.prestamosservice.domain.clients.IEventoUnidadPendiente;
import org.mockito.Mockito;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** SCRUM-24: listar préstamos por persona y por estado. Dominio puro, base e inventario simulados. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit tests — listar préstamos en PrestamoServiceImpl")
class ListadoPrestamosServiceTest {

    @Mock
    private IPrestamoDao prestamoDao;

    @Mock
    private IInventarioClient inventarioClient;


    private PrestamoServiceImpl servicio() {
        Clock reloj = Clock.fixed(
                Instant.parse("2026-11-20T15:00:00Z"),
                ZoneId.of("America/Bogota")
        );

        return new PrestamoServiceImpl(
                prestamoDao,
                inventarioClient,
                new FechaServiceImpl(reloj),
                Mockito.mock(IEventoUnidadPendiente.class)
        );
    }

    private static Prestamo prestamo(String persona) {
        return Prestamo.solicitar(14L, 2L, persona, 7, LocalDate.of(2026, 11, 20));
    }

    @Test
    @DisplayName("listar — solo persona → los préstamos de esa persona (CA-4)")
    void listar_soloPersona_debePedirLosDeEsaPersona() {
        List<Prestamo> deAna = List.of(prestamo("ana"));
        when(prestamoDao.findBySolicitanteIdOrderByIdDesc("ana")).thenReturn(deAna);

        assertThat(servicio().listar("ana", null)).isEqualTo(deAna);
        verifyNoMoreInteractions(prestamoDao);
    }

    @Test
    @DisplayName("listar — solo estado → los de ese estado, de cualquier persona (CA-6)")
    void listar_soloEstado_debePedirLosDeEseEstado() {
        List<Prestamo> pendientes = List.of(prestamo("ana"), prestamo("luis"));
        when(prestamoDao.findByEstadoOrderByIdDesc(EstadoPrestamo.SOLICITADO)).thenReturn(pendientes);

        assertThat(servicio().listar(null, EstadoPrestamo.SOLICITADO)).isEqualTo(pendientes);
        verifyNoMoreInteractions(prestamoDao);
    }

    @Test
    @DisplayName("listar — persona y estado → se combinan")
    void listar_personaYEstado_debeCombinarLosDosFiltros() {
        List<Prestamo> resultado = List.of(prestamo("ana"));
        when(prestamoDao.findBySolicitanteIdAndEstadoOrderByIdDesc("ana", EstadoPrestamo.ENTREGADO))
                .thenReturn(resultado);

        assertThat(servicio().listar("ana", EstadoPrestamo.ENTREGADO)).isEqualTo(resultado);
        verifyNoMoreInteractions(prestamoDao);
    }

    @Test
    @DisplayName("listar — sin filtros → todos, el más reciente primero")
    void listar_sinFiltros_debePedirTodos() {
        List<Prestamo> todos = List.of(prestamo("luis"), prestamo("ana"));
        when(prestamoDao.findAllByOrderByIdDesc()).thenReturn(todos);

        assertThat(servicio().listar(null, null)).isEqualTo(todos);
        verifyNoMoreInteractions(prestamoDao);
    }

    @Test
    @DisplayName("listar — la persona se recorta como al guardarla: «ana » y «ana» son la misma")
    void listar_personaConEspacios_debeRecortarla() {
        when(prestamoDao.findBySolicitanteIdOrderByIdDesc("ana")).thenReturn(List.of());

        servicio().listar("  ana ", null);

        verify(prestamoDao).findBySolicitanteIdOrderByIdDesc("ana");
    }

    @Test
    @DisplayName("listar (CA-7) — persona sin préstamos → lista vacía, no error")
    void listar_personaSinPrestamos_debeDevolverListaVacia() {
        when(prestamoDao.findBySolicitanteIdOrderByIdDesc("nadie")).thenReturn(List.of());

        assertThat(servicio().listar("nadie", null)).isEmpty();
    }

    @Test
    @DisplayName("listar — no consulta a inventario")
    void listar_noDebeConsultarInventario() {
        when(prestamoDao.findAllByOrderByIdDesc()).thenReturn(List.of());

        servicio().listar(null, null);

        verifyNoInteractions(inventarioClient);
    }
}
