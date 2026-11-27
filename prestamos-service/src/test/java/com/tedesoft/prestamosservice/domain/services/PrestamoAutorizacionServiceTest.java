
package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.clients.IAlmacenamientoFotos;
import com.tedesoft.prestamosservice.domain.clients.IEventoUnidadPendiente;
import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;
import com.tedesoft.prestamosservice.domain.models.EstadoUnidad;
import com.tedesoft.prestamosservice.domain.models.UnidadInventario;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas del servicio de autorización con inventario,
 * persistencia, eventos y almacenamiento simulados.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Servicio de autorización de préstamos")
class PrestamoAutorizacionServiceTest {

    private static final ZoneId BOGOTA =
            ZoneId.of("America/Bogota");

    private static final LocalDate FECHA_SOLICITUD =
            LocalDate.of(2026, 11, 2);

    @Mock
    private IPrestamoDao prestamoDao;

    @Mock
    private IInventarioClient inventarioClient;

    @Mock
    private IEventoUnidadPendiente eventosPendientes;

    @Mock
    private IAlmacenamientoFotos almacenamientoFotos;

    private PrestamoServiceImpl servicio;

    @BeforeEach
    void configurar() {

        // El reloj representa el 4 de noviembre:
        // dos días después de la solicitud original.
        Clock reloj = Clock.fixed(
                Instant.parse("2026-11-04T15:00:00Z"),
                BOGOTA
        );

        servicio = new PrestamoServiceImpl(
                prestamoDao,
                inventarioClient,
                new FechaServiceImpl(reloj),
                eventosPendientes,
                (clave, accion) -> accion.get(),
                almacenamientoFotos
        );
    }

    // ==========================================
    // REGLA INFORMADA POR INVENTARIO
    // ==========================================

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("El estado inicial se obtiene de la regla de inventario")
    void solicitarSegunInventario(
            boolean requiereAutorizacion) {

        UnidadInventario unidad = new UnidadInventario(
                14L,
                "U-14",
                EstadoUnidad.DISPONIBLE,
                2L,
                7,
                requiereAutorizacion
        );

        when(inventarioClient.obtenerUnidad(14L))
                .thenReturn(unidad);

        when(prestamoDao.saveAndFlush(any(Prestamo.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Prestamo resultado = servicio.solicitar(
                14L, "ana"
        );

        EstadoPrestamo esperado = requiereAutorizacion
                ? EstadoPrestamo.PENDIENTE_AUTORIZACION
                : EstadoPrestamo.SOLICITADO;

        assertThat(resultado.getEstado())
                .isEqualTo(esperado);

        assertThat(resultado.getDuracionAplicadaDias())
                .isEqualTo(7);

        verify(prestamoDao)
                .saveAndFlush(any(Prestamo.class));

        // Solicitar no equivale a entregar una unidad.
        verifyNoInteractions(
                eventosPendientes,
                almacenamientoFotos
        );
    }

    // ==========================================
    // AUTORIZACION DOS DIAS DESPUES
    // ==========================================

    @Test
    @DisplayName("Autorizar dos días después no cambia la fecha prevista")
    void autorizarDosDiasDespues() {

        Prestamo pendiente = Prestamo.solicitar(
                14L,
                2L,
                "ana",
                7,
                FECHA_SOLICITUD,
                true
        );

        when(prestamoDao.buscarParaActualizar(1L))
                .thenReturn(Optional.of(pendiente));

        when(prestamoDao.save(pendiente))
                .thenReturn(pendiente);

        Prestamo autorizado = servicio.autorizar(1L);

        assertThat(autorizado.getEstado())
                .isEqualTo(EstadoPrestamo.SOLICITADO);

        // La fecha de solicitud sigue siendo el 2 de noviembre.
        assertThat(autorizado.getFechaSolicitud())
                .isEqualTo(LocalDate.of(2026, 11, 2));

        // La devolución sigue prevista para el 9,
        // no para el 11 de noviembre.
        assertThat(autorizado.getFechaPrevista())
                .isEqualTo(LocalDate.of(2026, 11, 9));

        assertThat(autorizado.getDuracionAplicadaDias())
                .isEqualTo(7);

        verify(prestamoDao)
                .buscarParaActualizar(1L);

        verify(prestamoDao).save(pendiente);

        // Autorizar no modifica el inventario,
        // no genera eventos y no requiere fotografías.
        verifyNoInteractions(
                inventarioClient,
                eventosPendientes,
                almacenamientoFotos
        );
    }

    // ==========================================
    // RECHAZAR ESTADOS INCORRECTOS
    // ==========================================

    @ParameterizedTest
    @EnumSource(
            value = EstadoPrestamo.class,
            names = {
                    "SOLICITADO",
                    "ENTREGADO",
                    "DEVUELTO"
            }
    )
    @DisplayName("Autorizar un estado incorrecto genera un conflicto")
    void autorizarEstadoIncorrecto(
            EstadoPrestamo estado) {

        Prestamo prestamo = Prestamo.solicitar(
                14L,
                2L,
                "ana",
                7,
                FECHA_SOLICITUD,
                false
        );

        if (estado == EstadoPrestamo.ENTREGADO
                || estado == EstadoPrestamo.DEVUELTO) {
            prestamo.entregar();
        }

        if (estado == EstadoPrestamo.DEVUELTO) {
            prestamo.recibir();
        }

        when(prestamoDao.buscarParaActualizar(1L))
                .thenReturn(Optional.of(prestamo));

        assertThatThrownBy(() -> servicio.autorizar(1L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining(estado.name());

        assertThat(prestamo.getEstado())
                .isEqualTo(estado);

        verify(prestamoDao, never()).save(any());

        verifyNoInteractions(
                inventarioClient,
                eventosPendientes,
                almacenamientoFotos
        );
    }

    // ==========================================
    // PRESTAMO INEXISTENTE
    // ==========================================

    @Test
    @DisplayName("Autorizar un préstamo inexistente genera un 404")
    void autorizarPrestamoInexistente() {

        when(prestamoDao.buscarParaActualizar(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.autorizar(99L))
                .isInstanceOf(
                        RecursoNoEncontradoException.class
                )
                .hasMessageContaining("99");

        verify(prestamoDao, never()).save(any());

        verifyNoInteractions(
                inventarioClient,
                eventosPendientes,
                almacenamientoFotos
        );
    }

    // ==========================================
    // ENTREGA CON FOTO SIN AUTORIZACION
    // ==========================================

    @Test
    @DisplayName("Entregar sin autorización no guarda fotografías ni eventos")
    void entregarPendienteConFoto() throws IOException {

        Prestamo pendiente = Prestamo.solicitar(
                14L,
                2L,
                "ana",
                7,
                FECHA_SOLICITUD,
                true
        );

        when(prestamoDao.buscarParaActualizar(1L))
                .thenReturn(Optional.of(pendiente));

        // Crear una imagen PNG válida para que la prueba
        // compruebe la autorización y no el formato de la foto.
        BufferedImage imagen = new BufferedImage(
                2, 2, BufferedImage.TYPE_INT_RGB
        );

        ByteArrayOutputStream salida =
                new ByteArrayOutputStream();

        assertThat(ImageIO.write(imagen, "png", salida))
                .isTrue();

        byte[] foto = salida.toByteArray();

        assertThatThrownBy(() ->
                servicio.entregarConFoto(
                        1L, foto, "image/png"
                )
        )
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(error ->
                        assertThat(error.getMessage())
                                .containsIgnoringCase("autoriz")
                );

        assertThat(pendiente.getEstado())
                .isEqualTo(
                        EstadoPrestamo.PENDIENTE_AUTORIZACION
                );

        assertThat(pendiente.getFotoEntregaClave())
                .isNull();

        verify(prestamoDao, never()).save(any());

        verifyNoInteractions(
                inventarioClient,
                eventosPendientes,
                almacenamientoFotos
        );
    }
}
