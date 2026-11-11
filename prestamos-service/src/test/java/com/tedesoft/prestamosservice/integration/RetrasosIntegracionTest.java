package com.tedesoft.prestamosservice.integration;

import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.entities.EstadoPrestamo;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-23 de punta a punta: HTTP → servicio → H2, con el reloj fijo en el 20 de noviembre de 2026. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RetrasosIntegracionTest.RelojFijoConfig.class)
@DisplayName("Integration tests — flujo HTTP → H2 del listado de retrasos")
class RetrasosIntegracionTest {

    private static final String RUTA = "/api/v1/prestamos-service/prestamos/retrasos";

    @TestConfiguration
    static class RelojFijoConfig {
        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZoneId.of("America/Bogota"));
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
        prestamoDao.deleteAll();
    }

    private void guardar(long unidadId, String persona, EstadoPrestamo estado, LocalDate prevista) {
        Prestamo p = Prestamo.solicitar(unidadId, 2L, persona, 7, prevista.minusDays(7));
        ReflectionTestUtils.setField(p, "estado", estado);
        prestamoDao.save(p);
    }

    @Test
    @DisplayName("CA-1 y CA-2: lista solo los vencidos entregados, con sus días de retraso")
    void listar_debeDevolverSoloLosVencidosConSusDias() throws Exception {
        guardar(1L, "ana", EstadoPrestamo.ENTREGADO, LocalDate.of(2026, 11, 17));
        guardar(2L, "luis", EstadoPrestamo.ENTREGADO, LocalDate.of(2026, 11, 10));
        guardar(3L, "eva", EstadoPrestamo.ENTREGADO, LocalDate.of(2026, 11, 20));
        guardar(6L, "ian", EstadoPrestamo.ENTREGADO, LocalDate.of(2026, 11, 25));
        guardar(4L, "leo", EstadoPrestamo.SOLICITADO, LocalDate.of(2026, 11, 1));
        guardar(5L, "mia", EstadoPrestamo.DEVUELTO, LocalDate.of(2026, 11, 1));

        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].unidadId").value(2))
                .andExpect(jsonPath("$[0].diasRetraso").value(10))
                .andExpect(jsonPath("$[1].unidadId").value(1))
                .andExpect(jsonPath("$[1].diasRetraso").value(3));
        verifyNoInteractions(inventarioClient);
    }

    @Test
    @DisplayName("CA-5: sin vencidos → 200 con lista vacía")
    void listar_sinVencidos_debeDevolverListaVacia() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
