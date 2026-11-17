
package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.clients.IAlmacenamientoFotos;
import com.tedesoft.prestamosservice.domain.clients.IEventoUnidadPendiente;
import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.FotoInvalidaException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrestamoFotosServiceTest {

    @Mock
    private IPrestamoDao prestamoDao;

    @Mock
    private IInventarioClient inventarioClient;

    @Mock
    private IFechaService fechaService;

    @Mock
    private IEventoUnidadPendiente eventosPendientes;

    @Mock
    private IAlmacenamientoFotos almacenamientoFotos;

    private PrestamoServiceImpl servicio;
    private byte[] imagenValida;

    @BeforeEach
    void configurar() throws IOException {

        lenient().when(fechaService.ahora())
                .thenReturn(OffsetDateTime.parse(
                        "2026-11-20T10:00:00-05:00"
                ));

        servicio = new PrestamoServiceImpl(
                prestamoDao,
                inventarioClient,
                fechaService,
                eventosPendientes,
                (clave, accion) -> accion.get()
        );

        servicio.configurarAlmacenamientoFotos(
                almacenamientoFotos
        );

        // Permite comprobar los callbacks de limpieza
        // sin abrir una transacción real de PostgreSQL.
        TransactionSynchronizationManager.initSynchronization();

        BufferedImage imagen = new BufferedImage(
                2, 2, BufferedImage.TYPE_INT_RGB
        );

        ByteArrayOutputStream salida =
                new ByteArrayOutputStream();

        assertTrue(ImageIO.write(imagen, "png", salida));
        imagenValida = salida.toByteArray();
    }

    @AfterEach
    void limpiar() {
        if (TransactionSynchronizationManager
                .isSynchronizationActive()) {
            TransactionSynchronizationManager
                    .clearSynchronization();
        }
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

    private void prepararPrestamo(Prestamo prestamo) {

        when(prestamoDao.buscarParaActualizar(1L))
                .thenReturn(Optional.of(prestamo));

        when(prestamoDao.save(prestamo))
                .thenReturn(prestamo);
    }

    private void simularFinalizacion(int estado) {

        TransactionSynchronizationManager
                .getSynchronizations()
                .forEach(s -> s.afterCompletion(estado));
    }

    @Test
    void entregarConFoto_debeGuardarClaveYEvento() {

        Prestamo prestamo = crearPrestamo();
        prepararPrestamo(prestamo);

        when(almacenamientoFotos.guardar(
                any(byte[].class), eq("image/png")
        )).thenReturn("prestamos/fotos/entrega-1");

        Prestamo resultado = servicio.entregarConFoto(
                1L, imagenValida, "image/png"
        );

        assertEquals(
                EstadoPrestamo.ENTREGADO,
                resultado.getEstado()
        );

        assertEquals(
                "prestamos/fotos/entrega-1",
                resultado.getFotoEntregaClave()
        );

        verify(prestamoDao).save(prestamo);

        verify(eventosPendientes).guardar(
                eq(14L),
                eq("PRESTADA"),
                any(OffsetDateTime.class)
        );

        simularFinalizacion(
                TransactionSynchronization.STATUS_COMMITTED
        );

        verify(almacenamientoFotos, never())
                .eliminar(anyString());
    }

    @Test
    void recibirConFoto_debeGuardarClaveYEvento() {

        Prestamo prestamo = crearPrestamo();
        prestamo.entregar();

        prepararPrestamo(prestamo);

        when(almacenamientoFotos.guardar(
                any(byte[].class), eq("image/png")
        )).thenReturn("prestamos/fotos/devolucion-1");

        Prestamo resultado = servicio.recibirConFoto(
                1L, imagenValida, "image/png"
        );

        assertEquals(
                EstadoPrestamo.DEVUELTO,
                resultado.getEstado()
        );

        assertEquals(
                "prestamos/fotos/devolucion-1",
                resultado.getFotoDevolucionClave()
        );

        verify(prestamoDao).save(prestamo);

        verify(eventosPendientes).guardar(
                eq(14L),
                eq("DISPONIBLE"),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void entregarConFoto_debeRechazarImagenInvalida() {

        byte[] contenidoInvalido = {1, 2, 3, 4};

        assertThrows(
                FotoInvalidaException.class,
                () -> servicio.entregarConFoto(
                        1L,
                        contenidoInvalido,
                        "image/png"
                )
        );

        verifyNoInteractions(almacenamientoFotos);
        verifyNoInteractions(eventosPendientes);
    }

    @Test
    void recibirConFoto_debeRechazarEstadoIncorrecto() {

        Prestamo prestamo = crearPrestamo();

        when(prestamoDao.buscarParaActualizar(1L))
                .thenReturn(Optional.of(prestamo));

        assertThrows(
                RuntimeException.class,
                () -> servicio.recibirConFoto(
                        1L, imagenValida, "image/png"
                )
        );

        verify(almacenamientoFotos, never())
                .guardar(any(), anyString());

        verify(prestamoDao, never()).save(any());
        verifyNoInteractions(eventosPendientes);
    }

    @Test
    void entregarConFoto_siFallaS3_noDebeGuardarPrestamo() {

        Prestamo prestamo = crearPrestamo();

        when(prestamoDao.buscarParaActualizar(1L))
                .thenReturn(Optional.of(prestamo));

        when(almacenamientoFotos.guardar(
                any(byte[].class), eq("image/png")
        )).thenThrow(
                new IllegalStateException("S3 no disponible")
        );

        assertThrows(
                IllegalStateException.class,
                () -> servicio.entregarConFoto(
                        1L, imagenValida, "image/png"
                )
        );

        verify(prestamoDao, never()).save(any());
        verifyNoInteractions(eventosPendientes);
    }

    @Test
    void entregarConFoto_siHayRollback_debeLimpiarS3() {

        Prestamo prestamo = crearPrestamo();

        when(prestamoDao.buscarParaActualizar(1L))
                .thenReturn(Optional.of(prestamo));

        when(almacenamientoFotos.guardar(
                any(byte[].class), eq("image/png")
        )).thenReturn("prestamos/fotos/temporal-1");

        when(prestamoDao.save(prestamo))
                .thenThrow(
                        new IllegalStateException(
                                "Error guardando en PostgreSQL"
                        )
                );

        assertThrows(
                IllegalStateException.class,
                () -> servicio.entregarConFoto(
                        1L, imagenValida, "image/png"
                )
        );

        // Simula la notificación de una transacción revertida.
        simularFinalizacion(
                TransactionSynchronization.STATUS_ROLLED_BACK
        );

        verify(almacenamientoFotos).eliminar(
                "prestamos/fotos/temporal-1"
        );

        verifyNoInteractions(eventosPendientes);
    }

    // ------------------------------------------------------------------ CA-3: pedir las fotos

    @Test
    void prestamoCerrado_tieneLasDosFotosRecuperables() {
        Prestamo prestamo = crearPrestamo();
        prestamo.entregar();
        prestamo.registrarFotoEntrega("prestamos/fotos/e");
        prestamo.recibir();
        prestamo.registrarFotoDevolucion("prestamos/fotos/d");
        when(prestamoDao.findById(1L)).thenReturn(Optional.of(prestamo));
        when(almacenamientoFotos.recuperar("prestamos/fotos/e"))
                .thenReturn(new IAlmacenamientoFotos.Foto(new byte[]{1}, "image/png"));
        when(almacenamientoFotos.recuperar("prestamos/fotos/d"))
                .thenReturn(new IAlmacenamientoFotos.Foto(new byte[]{2}, "image/jpeg"));

        assertArrayEquals(new byte[]{1}, servicio.obtenerFotoEntrega(1L).contenido());
        assertEquals("image/jpeg", servicio.obtenerFotoDevolucion(1L).tipoMime());
    }

    @Test
    void fotoDeDevolucion_antesDeDevolver_noExiste() {
        Prestamo prestamo = crearPrestamo();
        prestamo.entregar();
        prestamo.registrarFotoEntrega("prestamos/fotos/e");
        when(prestamoDao.findById(1L)).thenReturn(Optional.of(prestamo));

        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.obtenerFotoDevolucion(1L));

        assertTrue(error.getMessage().contains("devolución"));
        verifyNoInteractions(almacenamientoFotos);
    }

    @Test
    void foto_deUnPrestamoInexistente_lanza404() {
        when(prestamoDao.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.obtenerFotoEntrega(99L));
        verifyNoInteractions(almacenamientoFotos);
    }

    @Test
    void foto_conClaveHuerfana_siElObjetoYaNoExisteSePropaga404() {
        Prestamo prestamo = crearPrestamo();
        prestamo.entregar();
        prestamo.registrarFotoEntrega("prestamos/fotos/borrada");
        when(prestamoDao.findById(1L)).thenReturn(Optional.of(prestamo));
        when(almacenamientoFotos.recuperar("prestamos/fotos/borrada"))
                .thenThrow(new RecursoNoEncontradoException("La fotografía no fue encontrada"));

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.obtenerFotoEntrega(1L));
    }

}
