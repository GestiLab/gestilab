package com.tedesoft.prestamosservice.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contexto completo sin simular inventario: mientras no haya cliente real, actúa el provisional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Integration tests — solicitud con el cliente provisional de inventario")
class PrestamoSinClienteRealTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST — sin cliente HTTP real → 503 con 'inventario no disponible'")
    void solicitar_sinClienteReal_debeRetornar503() throws Exception {
        mockMvc.perform(post("/api/v1/prestamos-service/prestamos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\": 14, \"solicitanteId\": \"ana\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("inventario no disponible"));
    }
}
