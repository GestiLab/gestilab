package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.clients.EstadoUnidad;
import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.clients.UnidadInventario;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Dominio puro: inventario y base de datos simulados, reloj fijo en el 2 de noviembre de 2026. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit tests — PrestamoServiceImpl con Mockito")
class PrestamoServiceImplTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Mock
    private IPrestamoDao prestamoDao;

    @Mock
    private IInventarioClient inventarioClient;

    private PrestamoServiceImpl servicio;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), BOGOTA);
        servicio = new PrestamoServiceImpl(prestamoDao, inventarioClient, new FechaServiceImpl(reloj));
    }

    private static UnidadInventario unidad(EstadoUnidad estado, int duracionDias) {
        return new UnidadInventario(14L, estado, 2L, duracionDias, false);
    }

    private void guardarDevuelveElMismoPrestamo() {
        when(prestamoDao.save(any(Prestamo.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("solicitar — unidad disponible de tipo a 7 días → préstamo con devolución el 9 de noviembre")
    void solicitar_unidadDisponible_debeCrearPrestamoConFechaDelTipo() {
        when(inventarioClient.obtenerUnidad(14L)).thenReturn(unidad(EstadoUnidad.DISPONIBLE, 7));
        guardarDevuelveElMismoPrestamo();

        Prestamo prestamo = servicio.solicitar(14L, "ana");

        assertThat(prestamo.getFechaSolicitud()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(prestamo.getFechaPrevista()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.SOLICITADO);
        assertThat(prestamo.getUnidadId()).isEqualTo(14L);
        assertThat(prestamo.getTipoId()).isEqualTo(2L);
        assertThat(prestamo.getSolicitanteId()).isEqualTo("ana");
        verify(prestamoDao).save(any(Prestamo.class));
    }

    @Test
    @DisplayName("solicitar — guarda la duración aplicada aunque el tipo cambie después")
    void solicitar_debeGuardarLaDuracionAplicada() {
        when(inventarioClient.obtenerUnidad(14L)).thenReturn(unidad(EstadoUnidad.DISPONIBLE, 7));
        guardarDevuelveElMismoPrestamo();

        Prestamo prestamo = servicio.solicitar(14L, "ana");

        // Si luego el tipo pasa a 3 días, este préstamo conserva sus 7 días y su fecha
        assertThat(prestamo.getDuracionAplicadaDias()).isEqualTo(7);
    }

    @Test
    @DisplayName("solicitar — tipo nuevo de 3 días → devolución a 3 días con el mismo código (RQ-01)")
    void solicitar_tipoNuevoDeTresDias_debeAplicarSuReglaSinCambiarCodigo() {
        when(inventarioClient.obtenerUnidad(30L))
                .thenReturn(new UnidadInventario(30L, EstadoUnidad.DISPONIBLE, 9L, 3, true));
        guardarDevuelveElMismoPrestamo();

        Prestamo prestamo = servicio.solicitar(30L, "ana");

        assertThat(prestamo.getFechaPrevista()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(prestamo.getDuracionAplicadaDias()).isEqualTo(3);
        assertThat(prestamo.getTipoId()).isEqualTo(9L);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoUnidad.class, names = {"PRESTADA", "EN_REVISION", "FUERA_DE_SERVICIO"})
    @DisplayName("solicitar — unidad no disponible → ReglaNegocioException y no crea préstamo")
    void solicitar_unidadNoDisponible_debeRechazarSinCrearPrestamo(EstadoUnidad estado) {
        when(inventarioClient.obtenerUnidad(14L)).thenReturn(unidad(estado, 7));

        assertThatThrownBy(() -> servicio.solicitar(14L, "ana"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no está disponible");
        verify(prestamoDao, never()).save(any());
    }

    @Test
    @DisplayName("solicitar — unidad inexistente → propaga RecursoNoEncontradoException y no crea préstamo")
    void solicitar_unidadInexistente_debePropagarNoEncontrado() {
        when(inventarioClient.obtenerUnidad(999L))
                .thenThrow(new RecursoNoEncontradoException("Unidad 999 no encontrada"));

        assertThatThrownBy(() -> servicio.solicitar(999L, "ana"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(prestamoDao, never()).save(any());
    }

    @Test
    @DisplayName("solicitar — inventario caído → propaga DependenciaNoDisponibleException y no crea préstamo")
    void solicitar_inventarioCaido_debePropagarDependenciaNoDisponible() {
        when(inventarioClient.obtenerUnidad(14L))
                .thenThrow(new DependenciaNoDisponibleException("inventario no disponible"));

        assertThatThrownBy(() -> servicio.solicitar(14L, "ana"))
                .isInstanceOf(DependenciaNoDisponibleException.class)
                .hasMessage("inventario no disponible");
        verify(prestamoDao, never()).save(any());
    }

    @Test
    @DisplayName("obtenerPorId — id existente → devuelve el préstamo")
    void obtenerPorId_idExistente_debeRetornarPrestamo() {
        Prestamo existente = Prestamo.solicitar(14L, 2L, "ana", 7, LocalDate.of(2026, 11, 2));
        when(prestamoDao.findById(1L)).thenReturn(Optional.of(existente));

        assertThat(servicio.obtenerPorId(1L)).isSameAs(existente);
    }

    @Test
    @DisplayName("obtenerPorId — id inexistente → RecursoNoEncontradoException")
    void obtenerPorId_idInexistente_debeLanzarNoEncontrado() {
        when(prestamoDao.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtenerPorId(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Préstamo 99 no encontrado");
    }
}
