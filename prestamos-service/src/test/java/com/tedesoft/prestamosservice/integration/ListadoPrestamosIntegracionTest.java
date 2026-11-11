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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-24 de punta a punta: HTTP → servicio → H2. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Integration tests — flujo HTTP → H2 de los listados de préstamos")
class ListadoPrestamosIntegracionTest {

    private static final String RUTA = "/api/v1/prestamos-service/prestamos";

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

    private void guardar(long unidadId, String persona, EstadoPrestamo estado) {
        Prestamo p = Prestamo.solicitar(unidadId, 2L, persona, 7, LocalDate.of(2026, 11, 2));
        ReflectionTestUtils.setField(p, "estado", estado);
        prestamoDao.save(p);
    }

    private void datos() {
        guardar(1L, "ana", EstadoPrestamo.DEVUELTO);
        guardar(2L, "luis", EstadoPrestamo.SOLICITADO);
        guardar(3L, "ana", EstadoPrestamo.SOLICITADO);
    }

    @Test
    @DisplayName("CA-4: ?persona=ana → solo los de ana, el más reciente primero")
    void listar_porPersona_debeDevolverSoloLosDeEsaPersona() throws Exception {
        datos();

        mockMvc.perform(get(RUTA).param("persona", "ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].unidadId").value(3))
                .andExpect(jsonPath("$[1].unidadId").value(1))
                .andExpect(jsonPath("$[0].solicitanteId").value("ana"));
        verifyNoInteractions(inventarioClient);
    }

    @Test
    @DisplayName("CA-6: ?estado=SOLICITADO → solo los pendientes de entrega, de cualquier persona")
    void listar_porEstado_debeDevolverSoloEseEstado() throws Exception {
        datos();

        mockMvc.perform(get(RUTA).param("estado", "SOLICITADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].estado").value("SOLICITADO"))
                .andExpect(jsonPath("$[1].estado").value("SOLICITADO"));
    }

    @Test
    @DisplayName("persona y estado a la vez → la intersección")
    void listar_personaYEstado_debeDevolverLaInterseccion() throws Exception {
        datos();

        mockMvc.perform(get(RUTA).param("persona", "ana").param("estado", "SOLICITADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].unidadId").value(3));
    }

    @Test
    @DisplayName("CA-7: persona sin préstamos → 200 con lista vacía")
    void listar_personaSinPrestamos_debeDevolverListaVacia() throws Exception {
        datos();

        mockMvc.perform(get(RUTA).param("persona", "nadie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("la persona se recorta de punta a punta, también con espacios Unicode")
    void listar_personaConEspacios_debeEncontrarLaMismaPersona() throws Exception {
        datos();

        mockMvc.perform(get(RUTA).param("persona", "  ana" + (char) 0x2003))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("la ruta del listado no se confunde con /{id} ni con /retrasos")
    void listar_noDebeChocarConLasRutasVecinas() throws Exception {
        datos();

        mockMvc.perform(get(RUTA)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get(RUTA + "/retrasos")).andExpect(status().isOk());
        mockMvc.perform(get(RUTA + "/999999")).andExpect(status().isNotFound());
    }
}
