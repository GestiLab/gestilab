package com.tedesoft.prestamosservice.delivery.security;

import com.tedesoft.prestamosservice.delivery.rest.PrestamoRestController;
import com.tedesoft.prestamosservice.domain.clients.IAlmacenamientoFotos;
import com.tedesoft.prestamosservice.domain.clients.IEventoUnidadPendiente;
import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.models.EstadoUnidad;
import com.tedesoft.prestamosservice.domain.models.UnidadInventario;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;
import com.tedesoft.prestamosservice.domain.services.IControlConcurrencia;
import com.tedesoft.prestamosservice.domain.services.IFechaService;
import com.tedesoft.prestamosservice.domain.services.PrestamoServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas HTTP de las solicitudes que requieren autorización.
 *
 * Se utilizan el controlador, las reglas de dominio y la
 * configuración de seguridad reales.
 *
 * Inventario, persistencia, eventos y almacenamiento
 * de fotografías se simulan con Mockito.
 */
@WebMvcTest(PrestamoRestController.class)
@Import({
        SeguridadConfig.class,
        DecodificadorDePruebaConfig.class,
        PrestamoServiceImpl.class,
        PrestamoAutorizacionRestTest.Configuracion.class
})
@DisplayName("HTTP: autorización previa de préstamos")
class PrestamoAutorizacionRestTest {

    private static final String BASE =
            "/api/v1/prestamos-service/prestamos";

    private static final LocalDate HOY =
            LocalDate.of(2026, 11, 2);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPrestamoDao prestamoDao;

    @MockitoBean
    private IInventarioClient inventarioClient;

    @MockitoBean
    private IEventoUnidadPendiente eventosPendientes;

    @MockitoBean
    private IAlmacenamientoFotos almacenamientoFotos;

    @MockitoBean
    private IFechaService fechaService;

    /**
     * Ejecuta la operación directamente.
     * El control de concurrencia real se comprueba
     * en ConcurrenciaPostgresTest.
     */
    @TestConfiguration
    static class Configuracion {

        @Bean
        IControlConcurrencia controlConcurrencia() {
            return (clave, accion) -> accion.get();
        }
    }

    private Prestamo crearPrestamo(
            boolean requiereAutorizacion) {

        Prestamo prestamo = Prestamo.solicitar(
                14L,
                2L,
                TokensDePrueba.SUJETO_USUARIO,
                7,
                HOY,
                requiereAutorizacion
        );

        ReflectionTestUtils.setField(
                prestamo, "id", 1L
        );

        return prestamo;
    }

    private void prepararInventario(
            boolean requiereAutorizacion) {

        when(inventarioClient.obtenerUnidad(14L))
                .thenReturn(new UnidadInventario(
                        14L,
                        "U-14",
                        EstadoUnidad.DISPONIBLE,
                        2L,
                        7,
                        requiereAutorizacion
                ));
    }

    // ==========================================
    // SOLICITAR UNA UNIDAD
    // ==========================================

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("POST devuelve 201 y el estado indicado por inventario")
    void solicitarSegunReglaDeInventario(
            boolean requiereAutorizacion)
            throws Exception {

        prepararInventario(requiereAutorizacion);

        when(fechaService.hoy()).thenReturn(HOY);

        when(prestamoDao.saveAndFlush(
                any(Prestamo.class)
        )).thenAnswer(inv -> {

            Prestamo prestamo = inv.getArgument(0);

            ReflectionTestUtils.setField(
                    prestamo, "id", 1L
            );

            return prestamo;
        });

        String estadoEsperado = requiereAutorizacion
                ? "PENDIENTE_AUTORIZACION"
                : "SOLICITADO";

        mockMvc.perform(post(BASE)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TokensDePrueba.usuario()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\":14}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.unidadId").value(14))
                .andExpect(jsonPath("$.estado")
                        .value(estadoEsperado))
                .andExpect(jsonPath("$.fechaSolicitud")
                        .value("2026-11-02"))
                .andExpect(jsonPath("$.fechaDevolucionPrevista")
                        .value("2026-11-09"))
                .andExpect(jsonPath("$.duracionAplicadaDias")
                        .value(7));

        verify(prestamoDao)
                .saveAndFlush(any(Prestamo.class));

        verifyNoInteractions(
                eventosPendientes,
                almacenamientoFotos
        );
    }

    // ==========================================
    // AUXILIAR AUTORIZA UNA SOLICITUD
    // ==========================================

    @Test
    @DisplayName("PATCH autorizar: auxiliar recibe 200 y estado SOLICITADO")
    void auxiliarPuedeAutorizar() throws Exception {

        Prestamo pendiente = crearPrestamo(true);

        when(prestamoDao.buscarParaActualizar(1L))
                .thenReturn(Optional.of(pendiente));

        when(prestamoDao.save(pendiente))
                .thenReturn(pendiente);

        mockMvc.perform(patch(BASE + "/1/autorizar")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TokensDePrueba.auxiliar()
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado")
                        .value("SOLICITADO"))
                .andExpect(jsonPath("$.fechaSolicitud")
                        .value("2026-11-02"))
                .andExpect(jsonPath("$.fechaDevolucionPrevista")
                        .value("2026-11-09"))
                .andExpect(jsonPath("$.duracionAplicadaDias")
                        .value(7));

        verify(prestamoDao)
                .buscarParaActualizar(1L);

        verify(prestamoDao).save(pendiente);

        // Autorizar no modifica el inventario.
        verifyNoInteractions(
                inventarioClient,
                eventosPendientes,
                almacenamientoFotos
        );
    }

    // ==========================================
    // SEGURIDAD: SIN TOKEN
    // ==========================================

    @Test
    @DisplayName("PATCH autorizar sin token devuelve 401")
    void sinTokenNoPuedeAutorizar() throws Exception {

        mockMvc.perform(
                        patch(BASE + "/1/autorizar")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error")
                        .value("Unauthorized"))
                .andExpect(jsonPath("$.mensaje")
                        .value(RespuestaErrorSeguridad.MENSAJE_401))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(
                prestamoDao,
                inventarioClient,
                eventosPendientes,
                almacenamientoFotos
        );
    }

    // ==========================================
    // SEGURIDAD: ROL USUARIO
    // ==========================================

    @Test
    @DisplayName("PATCH autorizar con rol usuario devuelve 403")
    void usuarioNoPuedeAutorizar() throws Exception {

        mockMvc.perform(patch(BASE + "/1/autorizar")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TokensDePrueba.usuario()
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error")
                        .value("Forbidden"))
                .andExpect(jsonPath("$.mensaje")
                        .value(RespuestaErrorSeguridad.MENSAJE_403))
                .andExpect(jsonPath("$.timestamp").exists());

        // El rechazo sucede antes de consultar la base.
        verifyNoInteractions(
                prestamoDao,
                inventarioClient,
                eventosPendientes,
                almacenamientoFotos
        );
    }

    // ==========================================
    // PRESTAMO INEXISTENTE
    // ==========================================

    @Test
    @DisplayName("Autorizar un préstamo inexistente devuelve 404")
    void autorizarInexistente() throws Exception {

        when(prestamoDao.buscarParaActualizar(99L))
                .thenReturn(Optional.empty());

        mockMvc.perform(patch(BASE + "/99/autorizar")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TokensDePrueba.auxiliar()
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error")
                        .value("Not Found"))
                .andExpect(jsonPath("$.mensaje")
                        .value(containsString("99")))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(prestamoDao, never()).save(any());

        verifyNoInteractions(
                eventosPendientes,
                almacenamientoFotos
        );
    }

    // ==========================================
    // AUTORIZAR UN ESTADO INCORRECTO
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
    @DisplayName("Autorizar un estado incorrecto devuelve 409")
    void autorizarEstadoIncorrecto(
            EstadoPrestamo estado) throws Exception {

        Prestamo prestamo = crearPrestamo(false);

        if (estado == EstadoPrestamo.ENTREGADO
                || estado == EstadoPrestamo.DEVUELTO) {
            prestamo.entregar();
        }

        if (estado == EstadoPrestamo.DEVUELTO) {
            prestamo.recibir();
        }

        when(prestamoDao.buscarParaActualizar(1L))
                .thenReturn(Optional.of(prestamo));

        mockMvc.perform(patch(BASE + "/1/autorizar")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TokensDePrueba.auxiliar()
                        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error")
                        .value("Conflict"))
                .andExpect(jsonPath("$.mensaje")
                        .value(containsString(estado.name())))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(prestamo.getEstado())
                .isEqualTo(estado);

        verify(prestamoDao, never()).save(any());

        verifyNoInteractions(
                eventosPendientes,
                almacenamientoFotos
        );
    }

    // ==========================================
    // ENTREGA SIN AUTORIZACION
    // ==========================================

    @Test
    @DisplayName("Entregar un préstamo pendiente devuelve 409 sin evento ni foto")
    void entregarSinAutorizacion() throws Exception {

        Prestamo pendiente = crearPrestamo(true);

        when(prestamoDao.buscarParaActualizar(1L))
                .thenReturn(Optional.of(pendiente));

        // Generamos una imagen PNG válida.
        BufferedImage imagen = new BufferedImage(
                2, 2, BufferedImage.TYPE_INT_RGB
        );

        ByteArrayOutputStream salida =
                new ByteArrayOutputStream();

        assertThat(
                ImageIO.write(imagen, "png", salida)
        ).isTrue();

        MockMultipartFile foto = new MockMultipartFile(
                "foto",
                "foto.png",
                "image/png",
                salida.toByteArray()
        );

        mockMvc.perform(
                        multipart(BASE + "/1/entregar")
                                .file(foto)
                                .with(req -> {
                                    req.setMethod("PATCH");
                                    return req;
                                })
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + TokensDePrueba.auxiliar()
                                )
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensaje")
                        .value(containsStringIgnoringCase("autoriz")));

        assertThat(pendiente.getEstado())
                .isEqualTo(
                        EstadoPrestamo.PENDIENTE_AUTORIZACION
                );

        assertThat(pendiente.getFotoEntregaClave())
                .isNull();

        verify(prestamoDao, never()).save(any());

        verifyNoInteractions(
                eventosPendientes,
                almacenamientoFotos
        );
    }

    // ==========================================
    // LIMITE DE TRES PRESTAMOS ACTIVOS
    // ==========================================

    @Test
    @DisplayName("Con tres préstamos activos una nueva solicitud devuelve 409")
    void topeDePrestamosActivos() throws Exception {

        when(prestamoDao.countBySolicitanteIdAndEstadoIn(
                TokensDePrueba.SUJETO_USUARIO,
                EstadoPrestamo.activos()
        )).thenReturn(3L);

        mockMvc.perform(post(BASE)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TokensDePrueba.usuario()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\":14}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensaje")
                        .value(containsString("tope de 3")));

        verify(prestamoDao, never())
                .saveAndFlush(any());

        verifyNoInteractions(
                inventarioClient,
                eventosPendientes,
                almacenamientoFotos
        );
    }

    // ==========================================
    // UNIDAD RESERVADA POR UN PRESTAMO ACTIVO
    // ==========================================

    @Test
    @DisplayName("Una unidad con préstamo activo no puede volver a solicitarse")
    void unidadConPrestamoActivo() throws Exception {

        prepararInventario(true);

        when(prestamoDao.existsByUnidadIdAndEstadoIn(
                14L, EstadoPrestamo.activos()
        )).thenReturn(true);

        mockMvc.perform(post(BASE)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TokensDePrueba.usuario()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\":14}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensaje")
                        .value(containsString("préstamo activo")));

        verify(prestamoDao, never())
                .saveAndFlush(any());

        verifyNoInteractions(
                eventosPendientes,
                almacenamientoFotos
        );
    }
}
