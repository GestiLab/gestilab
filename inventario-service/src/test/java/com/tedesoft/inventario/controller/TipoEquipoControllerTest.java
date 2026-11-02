package com.tedesoft.inventario.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tedesoft.inventario.dto.TipoEquipoRequestDto;
import com.tedesoft.inventario.dto.TipoEquipoResponseDto;
import com.tedesoft.inventario.exception.TipoEquipoNoEncontradoException;
import com.tedesoft.inventario.service.TipoEquipoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Capa web: valida los flujos CA-1 a CA-5 con el servicio simulado. */
@WebMvcTest(TipoEquipoController.class)
class TipoEquipoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TipoEquipoService service;

    // ---------------------------------------------------------------- CA-1

    @Test
    @DisplayName("CA-1: POST crea el tipo con 201 y al consultarlo devuelve exactamente los datos guardados")
    void crearMultimetro() throws Exception {
        var request = new TipoEquipoRequestDto("Multímetro", "Mide voltaje y corriente", 7, false);
        var creado = new TipoEquipoResponseDto(1L, "Multímetro", "Mide voltaje y corriente", 7, false);
        when(service.crear(request)).thenReturn(creado);
        when(service.obtenerPorId(1L)).thenReturn(creado);

        mockMvc.perform(post("/tipos-equipo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/tipos-equipo/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Multímetro"))
                .andExpect(jsonPath("$.duracionMaximaDias").value(7))
                .andExpect(jsonPath("$.requiereAutorizacionPrevia").value(false));

        mockMvc.perform(get("/tipos-equipo/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Multímetro"))
                .andExpect(jsonPath("$.descripcion").value("Mide voltaje y corriente"))
                .andExpect(jsonPath("$.duracionMaximaDias").value(7))
                .andExpect(jsonPath("$.requiereAutorizacionPrevia").value(false));
    }

    // ---------------------------------------------------------------- CA-2

    @Test
    @DisplayName("CA-2: un tipo creado con autorización previa la refleja al consultarlo")
    void crearCamaraConAutorizacion() throws Exception {
        var request = new TipoEquipoRequestDto("Cámara", "Cámara fotográfica", 3, true);
        var creado = new TipoEquipoResponseDto(2L, "Cámara", "Cámara fotográfica", 3, true);
        when(service.crear(request)).thenReturn(creado);
        when(service.obtenerPorId(2L)).thenReturn(creado);

        mockMvc.perform(post("/tipos-equipo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/tipos-equipo/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereAutorizacionPrevia").value(true));
    }

    // ---------------------------------------------------------------- CA-3

    @Test
    @DisplayName("CA-3: PUT cambia la duración a 3 días y la consulta posterior devuelve 3")
    void editarReglas() throws Exception {
        var request = new TipoEquipoRequestDto("Multímetro", "Mide voltaje y corriente", 3, false);
        var editado = new TipoEquipoResponseDto(1L, "Multímetro", "Mide voltaje y corriente", 3, false);
        when(service.actualizar(1L, request)).thenReturn(editado);
        when(service.obtenerPorId(1L)).thenReturn(editado);

        mockMvc.perform(put("/tipos-equipo/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionMaximaDias").value(3));

        mockMvc.perform(get("/tipos-equipo/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionMaximaDias").value(3));
    }

    // ---------------------------------------------------------------- CA-4

    static Stream<Arguments> peticionesInvalidas() {
        return Stream.of(
                Arguments.of("sin nombre", cuerpo(null, false, 7), "nombre"),
                Arguments.of("nombre en blanco", cuerpo("   ", true, 7), "nombre"),
                Arguments.of("duración cero", cuerpo("Multímetro", true, 0), "duracionMaximaDias"),
                Arguments.of("duración negativa", cuerpo("Multímetro", true, -3), "duracionMaximaDias"),
                Arguments.of("sin duración", cuerpo("Multímetro", true, null), "duracionMaximaDias")
        );
    }

    /** Arma el JSON como mapa para poder omitir campos (un record no permite "faltantes"). */
    private static Map<String, Object> cuerpo(String nombre, boolean autorizacion, Integer duracion) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        if (nombre != null) {
            mapa.put("nombre", nombre);
        }
        mapa.put("descripcion", "desc");
        if (duracion != null) {
            mapa.put("duracionMaximaDias", duracion);
        }
        mapa.put("requiereAutorizacionPrevia", autorizacion);
        return mapa;
    }

    @ParameterizedTest(name = "POST {0}")
    @MethodSource("peticionesInvalidas")
    @DisplayName("CA-4: POST con datos inválidos responde 400 con formato común y no llega al servicio")
    void crearConDatosInvalidos(String caso, Map<String, Object> payload, String campo) throws Exception {
        mockMvc.perform(post("/tipos-equipo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.mensaje").exists())
                .andExpect(jsonPath("$.ruta").value("/tipos-equipo"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem(campo)));

        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "PUT {0}")
    @MethodSource("peticionesInvalidas")
    @DisplayName("CA-4: PUT con datos inválidos responde 400 con formato común y no llega al servicio")
    void editarConDatosInvalidos(String caso, Map<String, Object> payload, String campo) throws Exception {
        mockMvc.perform(put("/tipos-equipo/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errores[*].campo", hasItem(campo)));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("CA-4: un JSON malformado también responde 400 con el formato común")
    void jsonMalformado() throws Exception {
        mockMvc.perform(post("/tipos-equipo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").exists());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Un id que no es numérico responde 400 con el formato común")
    void idNoNumerico() throws Exception {
        mockMvc.perform(get("/tipos-equipo/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("id")));
    }

    // ---------------------------------------------------------------- CA-5

    @Test
    @DisplayName("CA-5: GET de un id inexistente responde 404 con el formato común")
    void consultarInexistente() throws Exception {
        when(service.obtenerPorId(999L)).thenThrow(new TipoEquipoNoEncontradoException(999L));

        mockMvc.perform(get("/tipos-equipo/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.mensaje", containsString("999")))
                .andExpect(jsonPath("$.ruta").value("/tipos-equipo/999"));
    }

    @Test
    @DisplayName("CA-5: PUT de un id inexistente responde 404 con el formato común")
    void editarInexistente() throws Exception {
        when(service.actualizar(eq(999L), any(TipoEquipoRequestDto.class)))
                .thenThrow(new TipoEquipoNoEncontradoException(999L));

        mockMvc.perform(put("/tipos-equipo/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TipoEquipoRequestDto("Multímetro", "desc", 3, false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje", containsString("999")));
    }
}
