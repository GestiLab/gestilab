package com.tedesoft.prestamosservice.integration;

import com.tedesoft.prestamosservice.domain.clients.EstadoUnidad;
import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.clients.UnidadInventario;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controlador, servicio y JPA reales sobre H2; solo inventario se simula y el reloj se fija. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PrestamoIntegracionTest.RelojFijoConfig.class)
@DisplayName("Integration tests — flujo HTTP → H2 de solicitar un préstamo")
class PrestamoIntegracionTest {

    private static final String RUTA = "/api/v1/prestamos-service/prestamos";

    @TestConfiguration
    static class RelojFijoConfig {
        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneId.of("America/Bogota"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IPrestamoDao prestamoDao;

    @MockitoBean
    private IInventarioClient inventarioClient;

    @BeforeEach
    @AfterEach
    void limpiarBase() {
        // También al terminar: la base H2 en memoria se comparte con otras clases de prueba
        prestamoDao.deleteAll();
    }

    @Test
    @DisplayName("POST y luego GET — se persiste con la fecha del tipo y la duración aplicada")
    void solicitarYConsultar_debePersistirFechaYDuracion() throws Exception {
        when(inventarioClient.obtenerUnidad(14L))
                .thenReturn(new UnidadInventario(14L, EstadoUnidad.DISPONIBLE, 2L, 7, false));

        MvcResult creado = mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\": 14, \"solicitanteId\": \"ana\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaDevolucionPrevista").value("2026-11-09"))
                .andReturn();

        String location = creado.getResponse().getHeader("Location");
        assertThat(location).isNotNull();

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionAplicadaDias").value(7))
                .andExpect(jsonPath("$.fechaSolicitud").value("2026-11-02"))
                .andExpect(jsonPath("$.solicitanteId").value("ana"));
    }

    @Test
    @DisplayName("CA-2 — si el tipo cambia a 3 días después, el préstamo ya creado sigue con 7 días y su fecha")
    void consultar_trasCambiarElTipo_debeConservarLaDuracionAplicada() throws Exception {
        when(inventarioClient.obtenerUnidad(14L))
                .thenReturn(new UnidadInventario(14L, EstadoUnidad.DISPONIBLE, 2L, 7, false));

        MvcResult creado = mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\": 14, \"solicitanteId\": \"ana\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        // El tipo cambia en inventario: ahora presta por 3 días
        when(inventarioClient.obtenerUnidad(14L))
                .thenReturn(new UnidadInventario(14L, EstadoUnidad.DISPONIBLE, 2L, 3, false));

        mockMvc.perform(get(creado.getResponse().getHeader("Location")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionAplicadaDias").value(7))
                .andExpect(jsonPath("$.fechaDevolucionPrevista").value("2026-11-09"));
    }

    @Test
    @DisplayName("Una misma unidad no se presta a dos personas mientras el primer préstamo siga activo")
    void solicitar_unidadYaSolicitada_debeRechazarAlSegundo() throws Exception {
        when(inventarioClient.obtenerUnidad(14L))
                .thenReturn(new UnidadInventario(14L, EstadoUnidad.DISPONIBLE, 2L, 7, false));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\": 14, \"solicitanteId\": \"ana\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\": 14, \"solicitanteId\": \"luis\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La unidad 14 ya tiene un préstamo activo"));

        assertThat(prestamoDao.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST — unidad prestada → 409 y no queda ningún préstamo en la base")
    void solicitar_unidadPrestada_noDebeCrearPrestamo() throws Exception {
        when(inventarioClient.obtenerUnidad(14L))
                .thenReturn(new UnidadInventario(14L, EstadoUnidad.PRESTADA, 2L, 7, false));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\": 14, \"solicitanteId\": \"ana\"}"))
                .andExpect(status().isConflict());

        assertThat(prestamoDao.count()).isZero();
    }
}
