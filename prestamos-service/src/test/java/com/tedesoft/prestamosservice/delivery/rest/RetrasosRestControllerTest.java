package com.tedesoft.prestamosservice.delivery.rest;

import com.tedesoft.prestamosservice.domain.models.PrestamoRetrasado;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-23: capa web con el servicio simulado. */
@WebMvcTest(PrestamoRestController.class)
@DisplayName("Integration tests (web slice) — GET /prestamos/retrasos")
class RetrasosRestControllerTest {

    private static final String RUTA = "/api/v1/prestamos-service/prestamos/retrasos";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPrestamoService prestamoService;

    @Test
    @DisplayName("CA-1: con retrasos → 200 y cada uno con unidad, persona, fecha de devolución y días")
    void listar_conRetrasos_debeRetornar200ConLosDatos() throws Exception {
        when(prestamoService.listarRetrasados()).thenReturn(List.of(
                new PrestamoRetrasado(1L, 14L, "ana", LocalDate.of(2026, 11, 17), 3),
                new PrestamoRetrasado(2L, 15L, "luis", LocalDate.of(2026, 11, 10), 10)));

        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].prestamoId").value(1))
                .andExpect(jsonPath("$[0].unidadId").value(14))
                .andExpect(jsonPath("$[0].solicitanteId").value("ana"))
                .andExpect(jsonPath("$[0].fechaDevolucionPrevista").value("2026-11-17"))
                .andExpect(jsonPath("$[0].diasRetraso").value(3))
                .andExpect(jsonPath("$[1].diasRetraso").value(10));
        verify(prestamoService).listarRetrasados();
    }

    @Test
    @DisplayName("CA-5: sin retrasos → 200 con lista vacía")
    void listar_sinRetrasos_debeRetornar200ConListaVacia() throws Exception {
        when(prestamoService.listarRetrasados()).thenReturn(List.of());

        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
