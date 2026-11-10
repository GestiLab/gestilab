package com.tedesoft.prestamosservice.delivery.rest;

import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.exception.ReglaNegocioException;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("cuerposInvalidos")
    @DisplayName("POST — cuerpo que no cumple el contrato → 400 y no llama al servicio")
    void solicitar_cuerpoInvalido_debeRetornar400SinLlamarAlServicio(String caso, String cuerpo) throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.trace").doesNotExist());

        verifyNoInteractions(prestamoService);
    }

    static Stream<Arguments> cuerposInvalidos() {
        return Stream.of(
                Arguments.of("unidadId cero", "{\"unidadId\": 0, \"solicitanteId\": \"ana\"}"),
                Arguments.of("unidadId negativo", "{\"unidadId\": -1, \"solicitanteId\": \"ana\"}"),
                Arguments.of("unidadId decimal no se trunca", "{\"unidadId\": 1.5, \"solicitanteId\": \"ana\"}"),
                Arguments.of("unidadId decimal 14.9 no presta la 14", "{\"unidadId\": 14.9, \"solicitanteId\": \"ana\"}"),
                Arguments.of("unidadId como texto", "{\"unidadId\": \"14\", \"solicitanteId\": \"ana\"}"),
                Arguments.of("basura después del JSON", "{\"unidadId\": 14, \"solicitanteId\": \"ana\"} xxx"),
                Arguments.of("solicitanteId de 101 caracteres",
                        "{\"unidadId\": 14, \"solicitanteId\": \"" + "a".repeat(101) + "\"}"),
                Arguments.of("solicitanteId solo con espacio Unicode (U+2003)",
                        "{\"unidadId\": 14, \"solicitanteId\": \"\\u2003\"}"),
                Arguments.of("solicitanteId con carácter nulo",
                        "{\"unidadId\": 14, \"solicitanteId\": \"ana\\u0000\"}"));
    }

    @Test
    @DisplayName("POST — solicitante de 100 caracteres con acentos y espacios alrededor → 201 y se recorta")
    void solicitar_solicitanteEnElLimite_debeAceptarseYRecortarse() throws Exception {
        String nombre = "á".repeat(100);
        when(prestamoService.solicitar(14L, nombre)).thenReturn(prestamoGuardado());

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\": 14, \"solicitanteId\": \"  " + nombre + "  \"}"))
                .andExpect(status().isCreated());

        verify(prestamoService).solicitar(14L, nombre);
    }

    @Test
    @DisplayName("POST — Accept distinto de JSON → 406 y el préstamo NO se crea")
    void solicitar_acceptNoJson_debeRetornar406SinCrearPrestamo() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN).content(CUERPO))
                .andExpect(status().isNotAcceptable());

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
