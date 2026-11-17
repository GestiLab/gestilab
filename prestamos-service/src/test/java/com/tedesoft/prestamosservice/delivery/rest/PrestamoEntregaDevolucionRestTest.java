
package com.tedesoft.prestamosservice.delivery.rest;

import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PrestamoRestController.class)
@DisplayName("SCRUM-19/SCRUM-22: Pruebas HTTP de entrega y devolución (con fotografía)")
class PrestamoEntregaDevolucionRestTest {

    private static final String RUTA =
            "/api/v1/prestamos-service/prestamos";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPrestamoService prestamoService;

    private Prestamo crearPrestamo() {
        Prestamo prestamo = Prestamo.solicitar(
                14L,
                2L,
                "ana",
                7,
                LocalDate.of(2026, 11, 13)
        );

        ReflectionTestUtils.setField(
                prestamo, "id", 1L
        );

        return prestamo;
    }


    /** PATCH multipart con la parte foto (un PNG mínimo): la entrega y la devolución exigen fotografía. */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder conFoto(String ruta) {
        MockMultipartFile foto = new MockMultipartFile(
                "foto", "foto.png", "image/png", new byte[]{1, 2, 3});
        return multipart(RUTA + ruta)
                .file(foto)
                .with(request -> {
                    request.setMethod("PATCH");
                    return request;
                })
                .accept(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("Entregar responde 200 y ENTREGADO")
    void entregar_correctamente() throws Exception {
        Prestamo prestamo = crearPrestamo();
        prestamo.entregar();

        when(prestamoService.entregarConFoto(eq(1L), any(byte[].class), eq("image/png")))
                .thenReturn(prestamo);

        mockMvc.perform(conFoto("/1/entregar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.unidadId").value(14))
                .andExpect(jsonPath("$.estado").value("ENTREGADO"));

        verify(prestamoService).entregarConFoto(eq(1L), any(byte[].class), eq("image/png"));
    }

    @Test
    @DisplayName("Recibir responde 200 y DEVUELTO")
    void recibir_correctamente() throws Exception {
        Prestamo prestamo = crearPrestamo();
        prestamo.entregar();
        prestamo.recibir();

        when(prestamoService.recibirConFoto(eq(1L), any(byte[].class), eq("image/png")))
                .thenReturn(prestamo);

        mockMvc.perform(conFoto("/1/recibir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.estado").value("DEVUELTO"));

        verify(prestamoService).recibirConFoto(eq(1L), any(byte[].class), eq("image/png"));
    }

    @Test
    @DisplayName("Entregar préstamo inexistente responde 404")
    void entregar_inexistente() throws Exception {
        when(prestamoService.entregarConFoto(eq(99L), any(byte[].class), eq("image/png")))
                .thenThrow(new RecursoNoEncontradoException(
                        "Préstamo 99 no encontrado"
                ));

        mockMvc.perform(conFoto("/99/entregar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Recibir préstamo inexistente responde 404")
    void recibir_inexistente() throws Exception {
        when(prestamoService.recibirConFoto(eq(99L), any(byte[].class), eq("image/png")))
                .thenThrow(new RecursoNoEncontradoException(
                        "Préstamo 99 no encontrado"
                ));

        mockMvc.perform(conFoto("/99/recibir"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Entrega inválida responde 409")
    void entregar_estadoInvalido() throws Exception {
        when(prestamoService.entregarConFoto(eq(1L), any(byte[].class), eq("image/png")))
                .thenThrow(new ReglaNegocioException(
                        "No se puede entregar el préstamo"
                ));

        mockMvc.perform(conFoto("/1/entregar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Devolución inválida responde 409")
    void recibir_estadoInvalido() throws Exception {
        when(prestamoService.recibirConFoto(eq(1L), any(byte[].class), eq("image/png")))
                .thenThrow(new ReglaNegocioException(
                        "No se puede recibir el préstamo"
                ));

        mockMvc.perform(conFoto("/1/recibir"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Entrega con identificador no numérico responde 400")
    void entregar_idInvalido() throws Exception {
        mockMvc.perform(conFoto("/abc/entregar"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("Devolución con identificador no numérico responde 400")
    void recibir_idInvalido() throws Exception {
        mockMvc.perform(conFoto("/abc/recibir"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(prestamoService);
    }
}
