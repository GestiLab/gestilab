
package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.clients.IEventoUnidadPendiente;
import org.mockito.Mockito;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SCRUM-19: Entrega y devolución")
class PrestamoEntregaDevolucionServiceTest {

    @Mock
    private IPrestamoDao prestamoDao;

    @Mock
    private IInventarioClient inventarioClient;

    @Mock
    private IFechaService fechaService;

    @Mock
    private IEventoUnidadPendiente eventosPendientes;

    private PrestamoServiceImpl servicio;

    @BeforeEach
    void configurar() {
        servicio = new PrestamoServiceImpl(
                prestamoDao,
                inventarioClient,
                fechaService,
                eventosPendientes
        );
    }

    private Prestamo crearPrestamo() {
        return Prestamo.solicitar(
                14L,
                2L,
                "ana",
                7,
                LocalDate.of(2026, 11, 13)
        );
    }

    @Test
    @DisplayName("Entregar un préstamo solicitado")
    void entregar_debeGuardarEstadoEntregado() {
        Prestamo prestamo = crearPrestamo();

        when(prestamoDao.findById(1L))
                .thenReturn(Optional.of(prestamo));

        when(prestamoDao.save(prestamo))
                .thenReturn(prestamo);

        Prestamo resultado = servicio.entregar(1L);

        assertThat(resultado.getEstado())
                .isEqualTo(EstadoPrestamo.ENTREGADO);

        verify(prestamoDao).save(prestamo);

        verify(eventosPendientes).guardar(
                eq(14L),
                eq("PRESTADA"),
                any(OffsetDateTime.class)
        );

        verifyNoInteractions(inventarioClient);
    }

    @Test
    @DisplayName("Recibir un préstamo entregado")
    void recibir_debeGuardarEstadoDevuelto() {
        Prestamo prestamo = crearPrestamo();
        prestamo.entregar();

        when(prestamoDao.findById(1L))
                .thenReturn(Optional.of(prestamo));

        when(prestamoDao.save(prestamo))
                .thenReturn(prestamo);

        Prestamo resultado = servicio.recibir(1L);

        assertThat(resultado.getEstado())
                .isEqualTo(EstadoPrestamo.DEVUELTO);

        verify(prestamoDao).save(prestamo);

        verify(eventosPendientes).guardar(
                eq(14L),
                eq("DISPONIBLE"),
                any(OffsetDateTime.class)
        );

        verifyNoInteractions(inventarioClient);
    }

    @Test
    @DisplayName("No entregar dos veces")
    void entregar_duplicado_debeRechazar() {
        Prestamo prestamo = crearPrestamo();
        prestamo.entregar();

        when(prestamoDao.findById(1L))
                .thenReturn(Optional.of(prestamo));

        assertThatThrownBy(() -> servicio.entregar(1L))
                .isInstanceOf(ReglaNegocioException.class);

        verify(prestamoDao, never()).save(any());

        verifyNoInteractions(eventosPendientes);
    }

    @Test
    @DisplayName("No devolver un préstamo solicitado")
    void recibir_sinEntrega_debeRechazar() {
        Prestamo prestamo = crearPrestamo();

        when(prestamoDao.findById(1L))
                .thenReturn(Optional.of(prestamo));

        assertThatThrownBy(() -> servicio.recibir(1L))
                .isInstanceOf(ReglaNegocioException.class);

        verify(prestamoDao, never()).save(any());

        verifyNoInteractions(eventosPendientes);
    }

    @Test
    @DisplayName("No entregar un préstamo inexistente")
    void entregar_inexistente_debeRetornarError() {
        when(prestamoDao.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.entregar(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(prestamoDao, never()).save(any());

        verifyNoInteractions(eventosPendientes);
    }

    @Test
    @DisplayName("No recibir un préstamo inexistente")
    void recibir_inexistente_debeRetornarError() {
        when(prestamoDao.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.recibir(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(prestamoDao, never()).save(any());
        verifyNoInteractions(eventosPendientes);
    }
}
