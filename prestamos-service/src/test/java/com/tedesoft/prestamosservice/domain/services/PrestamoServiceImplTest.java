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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @DisplayName("solicitar — guarda la duración que informó inventario en ese momento")
    void solicitar_debeGuardarLaDuracionAplicada() {
        when(inventarioClient.obtenerUnidad(14L)).thenReturn(unidad(EstadoUnidad.DISPONIBLE, 7));
        guardarDevuelveElMismoPrestamo();

        assertThat(servicio.solicitar(14L, "ana").getDuracionAplicadaDias()).isEqualTo(7);
        // Que el préstamo la conserve aunque el tipo cambie después (CA-2) lo ejerce PrestamoIntegracionTest
    }

    @ParameterizedTest(name = "mismo tipo con {0} días → devolución el {1}")
    @CsvSource({"3,2026-11-05", "5,2026-11-07", "7,2026-11-09", "30,2026-12-02", "365,2027-11-02"})
    @DisplayName("solicitar — la fecha sale de la duración que informa inventario, no de una tabla por tipo (RQ-01)")
    void solicitar_cualquierDuracionDelTipo_debeAplicarlaSinCambiarCodigo(int duracion, LocalDate esperada) {
        when(inventarioClient.obtenerUnidad(14L))
                .thenReturn(new UnidadInventario(14L, EstadoUnidad.DISPONIBLE, 2L, duracion, true));
        guardarDevuelveElMismoPrestamo();

        Prestamo prestamo = servicio.solicitar(14L, "ana");

        assertThat(prestamo.getFechaPrevista()).isEqualTo(esperada);
        assertThat(prestamo.getDuracionAplicadaDias()).isEqualTo(duracion);
        assertThat(prestamo.getTipoId()).isEqualTo(2L);
    }

    @ParameterizedTest
    @CsvSource({"PRESTADA,prestada", "EN_REVISION,en revisión", "FUERA_DE_SERVICIO,fuera de servicio"})
    @DisplayName("solicitar — unidad no disponible → 409 con el estado legible y no crea préstamo")
    void solicitar_unidadNoDisponible_debeRechazarExplicandoElEstado(EstadoUnidad estado, String texto) {
        when(inventarioClient.obtenerUnidad(14L)).thenReturn(unidad(estado, 7));

        assertThatThrownBy(() -> servicio.solicitar(14L, "ana"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La unidad 14 no está disponible (estado: " + texto + ")");
        verify(prestamoDao, never()).save(any());
    }

    @Test
    @DisplayName("solicitar — unidad ya en un préstamo activo → 409 aunque inventario la vea disponible")
    void solicitar_unidadConPrestamoActivo_debeRechazarSinCrearPrestamo() {
        when(inventarioClient.obtenerUnidad(14L)).thenReturn(unidad(EstadoUnidad.DISPONIBLE, 7));
        when(prestamoDao.existsByUnidadIdAndEstadoIn(14L, EstadoPrestamo.activos())).thenReturn(true);

        assertThatThrownBy(() -> servicio.solicitar(14L, "luis"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La unidad 14 ya tiene un préstamo activo");
        verify(prestamoDao, never()).save(any());
    }

    @Test
    @DisplayName("solicitar — unidad cuyo último préstamo ya se devolvió → se puede volver a solicitar")
    void solicitar_unidadSinPrestamoActivo_debeCrearPrestamo() {
        when(inventarioClient.obtenerUnidad(14L)).thenReturn(unidad(EstadoUnidad.DISPONIBLE, 7));
        when(prestamoDao.existsByUnidadIdAndEstadoIn(14L, EstadoPrestamo.activos())).thenReturn(false);
        guardarDevuelveElMismoPrestamo();

        assertThat(servicio.solicitar(14L, "luis").getSolicitanteId()).isEqualTo("luis");
    }

    @Test
    @DisplayName("solicitar — respuesta de inventario nula → 503, no un error interno")
    void solicitar_respuestaNula_debeTratarseComoDependenciaCaida() {
        when(inventarioClient.obtenerUnidad(14L)).thenReturn(null);

        assertThatThrownBy(() -> servicio.solicitar(14L, "ana"))
                .isInstanceOf(DependenciaNoDisponibleException.class);
        verify(prestamoDao, never()).save(any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("respuestasQueIncumplenElContrato")
    @DisplayName("solicitar — respuesta de inventario que incumple el contrato → 503 y no crea préstamo")
    void solicitar_respuestaIncoherente_debeTratarseComoDependenciaCaida(String caso, UnidadInventario respuesta) {
        when(inventarioClient.obtenerUnidad(14L)).thenReturn(respuesta);

        assertThatThrownBy(() -> servicio.solicitar(14L, "ana"))
                .isInstanceOf(DependenciaNoDisponibleException.class)
                .hasMessage("inventario respondió con datos inválidos");
        verify(prestamoDao, never()).save(any());
    }

    static Stream<Arguments> respuestasQueIncumplenElContrato() {
        return Stream.of(
                Arguments.of("estado nulo", new UnidadInventario(14L, null, 2L, 7, false)),
                Arguments.of("tipo nulo", new UnidadInventario(14L, EstadoUnidad.DISPONIBLE, null, 7, false)),
                Arguments.of("duración cero", new UnidadInventario(14L, EstadoUnidad.DISPONIBLE, 2L, 0, false)),
                Arguments.of("duración negativa", new UnidadInventario(14L, EstadoUnidad.DISPONIBLE, 2L, -1, false)));
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

    private void personaConActivos(String solicitante, long activos) {
        when(prestamoDao.countBySolicitanteIdAndEstadoIn(solicitante, EstadoPrestamo.activos()))
                .thenReturn(activos);
    }

    @Test
    @DisplayName("tope — persona con 2 préstamos activos → el préstamo se crea")
    void solicitar_conDosActivos_debeCrearPrestamo() {
        personaConActivos("ana", 2);
        when(inventarioClient.obtenerUnidad(14L)).thenReturn(unidad(EstadoUnidad.DISPONIBLE, 7));
        guardarDevuelveElMismoPrestamo();

        assertThat(servicio.solicitar(14L, "ana").getSolicitanteId()).isEqualTo("ana");
    }

    @ParameterizedTest
    @ValueSource(longs = {3, 4})
    @DisplayName("tope — persona con 3 o más activos → rechazo que indica el tope y no crea préstamo")
    void solicitar_enElTope_debeRechazarIndicandoElTope(long activos) {
        personaConActivos("ana", activos);

        assertThatThrownBy(() -> servicio.solicitar(14L, "ana"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("tope de 3 unidades");
        verify(prestamoDao, never()).save(any());
        verifyNoInteractions(inventarioClient);
    }

    @Test
    @DisplayName("tope — al devolver una y quedar con 2 activos → puede solicitar otra")
    void solicitar_trasUnaDevolucion_debeCrearPrestamo() {
        when(prestamoDao.countBySolicitanteIdAndEstadoIn("ana", EstadoPrestamo.activos()))
                .thenReturn(3L, 2L);
        when(inventarioClient.obtenerUnidad(14L)).thenReturn(unidad(EstadoUnidad.DISPONIBLE, 7));
        guardarDevuelveElMismoPrestamo();

        assertThatThrownBy(() -> servicio.solicitar(14L, "ana"))
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(servicio.solicitar(14L, "ana").getEstado()).isEqualTo(EstadoPrestamo.SOLICITADO);
    }

    @Test
    @DisplayName("tope — otra persona con 0 activos no se afecta por quien ya llegó al tope")
    void solicitar_otraPersona_noDebeVerseAfectada() {
        personaConActivos("luis", 0);
        when(inventarioClient.obtenerUnidad(14L)).thenReturn(unidad(EstadoUnidad.DISPONIBLE, 7));
        guardarDevuelveElMismoPrestamo();

        assertThat(servicio.solicitar(14L, "luis").getSolicitanteId()).isEqualTo("luis");
    }

    @Test
    @DisplayName("tope — espacios en el solicitante no permiten saltarse el conteo")
    void solicitar_solicitanteConEspacios_debeContarComoLaMismaPersona() {
        personaConActivos("ana", 3);

        assertThatThrownBy(() -> servicio.solicitar(14L, "  ana  "))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("tope");
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
