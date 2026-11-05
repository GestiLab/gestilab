package com.tedesoft.prestamosservice.delivery.rest;

import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Capa web con el servicio simulado: no levanta inventario ni base de datos. */
@WebMvcTest(PrestamoRestController.class)
@DisplayName("Integration tests (web slice) — PrestamoRestController con MockMvc")
class PrestamoRestControllerTest {

    private static final String RUTA = "/api/v1/prestamos-service/prestamos";
    private static final String CUERPO = """
            {"unidadId": 14, "solicitanteId": "ana"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPrestamoService prestamoService;

    private static Prestamo prestamoGuardado() {
        Prestamo prestamo = Prestamo.solicitar(14L, 2L, "ana", 7, LocalDate.of(2026, 11, 2));
        ReflectionTestUtils.setField(prestamo, "id", 1L);
        return prestamo;
    }

    @Test
    @DisplayName("POST — unidad disponible → 201 con fecha, duración y Location")
    void solicitar_unidadDisponible_debeRetornar201() throws Exception {
        when(prestamoService.solicitar(14L, "ana")).thenReturn(prestamoGuardado());

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(RUTA + "/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.estado").value("SOLICITADO"))
                .andExpect(jsonPath("$.duracionAplicadaDias").value(7))
                .andExpect(jsonPath("$.fechaDevolucionPrevista").value("2026-11-09"));
    }

    @Test
    @DisplayName("POST — unidad no disponible → 409 con el motivo")
    void solicitar_unidadNoDisponible_debeRetornar409() throws Exception {
        when(prestamoService.solicitar(any(), any()))
                .thenThrow(new ReglaNegocioException("La unidad 14 no está disponible (estado: prestada)"));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensaje").value("La unidad 14 no está disponible (estado: prestada)"));
    }

    @Test
    @DisplayName("POST — unidad inexistente → 404 con formato de error común")
    void solicitar_unidadInexistente_debeRetornar404() throws Exception {
        when(prestamoService.solicitar(any(), any()))
                .thenThrow(new RecursoNoEncontradoException("Unidad 999 no encontrada"));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\": 999, \"solicitanteId\": \"ana\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.mensaje").value("Unidad 999 no encontrada"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST — inventario caído → 503 con 'inventario no disponible'")
    void solicitar_inventarioCaido_debeRetornar503() throws Exception {
        when(prestamoService.solicitar(any(), any()))
                .thenThrow(new DependenciaNoDisponibleException("inventario no disponible"));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.mensaje").value("inventario no disponible"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("POST — datos inválidos → 400 con errores por campo y sin llamar al servicio")
    void solicitar_datosInvalidos_debeRetornar400() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\": null, \"solicitanteId\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.unidadId").value("La unidad es obligatoria"))
                .andExpect(jsonPath("$.errores.solicitanteId").value("El solicitante es obligatorio"));

        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("GET /{id} — existente → 200 con la duración aplicada")
    void obtener_idExistente_debeRetornar200() throws Exception {
        when(prestamoService.obtenerPorId(1L)).thenReturn(prestamoGuardado());

        mockMvc.perform(get(RUTA + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionAplicadaDias").value(7))
                .andExpect(jsonPath("$.fechaDevolucionPrevista").value("2026-11-09"));
    }

    @Test
    @DisplayName("GET /{id} — id que no es número → 400, no 500")
    void obtener_idNoNumerico_debeRetornar400() throws Exception {
        mockMvc.perform(get(RUTA + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.id").value("El valor no tiene el formato esperado"));

        verifyNoInteractions(prestamoService);
    }

    @Test
    @DisplayName("GET /{id} — inexistente → 404")
    void obtener_idInexistente_debeRetornar404() throws Exception {
        when(prestamoService.obtenerPorId(99L))
                .thenThrow(new RecursoNoEncontradoException("Préstamo 99 no encontrado"));

        mockMvc.perform(get(RUTA + "/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Préstamo 99 no encontrado"));
    }
}
