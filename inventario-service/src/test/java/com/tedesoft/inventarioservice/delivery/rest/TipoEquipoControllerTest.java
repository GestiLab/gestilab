package com.tedesoft.inventarioservice.delivery.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tedesoft.inventarioservice.delivery.rest.dto.TipoEquipoRequestDto;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.inventarioservice.domain.services.TipoEquipoService;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Capa web con el servicio simulado: rutas, códigos HTTP y formato de error de docs/guia-errores.md. */
@WebMvcTest(TipoEquipoController.class)
class TipoEquipoControllerTest {

    private static final String RUTA = "/api/v1/inventario-service/tipos-equipo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TipoEquipoService service;

    private static TipoEquipo tipo(long id, String nombre, String descripcion, int dias, boolean autorizacion) {
        TipoEquipo tipo = TipoEquipo.crear(nombre, descripcion, dias, autorizacion);
        ReflectionTestUtils.setField(tipo, "id", id);
        return tipo;
    }

    @Test
    @DisplayName("POST crea el tipo con 201, Location y los datos guardados")
    void crearMultimetro() throws Exception {
        var request = new TipoEquipoRequestDto("Multímetro", "Mide voltaje y corriente", 7, false);
        var creado = tipo(1L, "Multímetro", "Mide voltaje y corriente", 7, false);
        when(service.crear("Multímetro", "Mide voltaje y corriente", 7, false)).thenReturn(creado);
        when(service.obtenerPorId(1L)).thenReturn(creado);

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(RUTA + "/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Multímetro"))
                .andExpect(jsonPath("$.duracionMaximaDias").value(7))
                .andExpect(jsonPath("$.requiereAutorizacion").value(false));

        mockMvc.perform(get(RUTA + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Mide voltaje y corriente"))
                .andExpect(jsonPath("$.duracionMaximaDias").value(7));
    }

    @Test
    @DisplayName("un tipo con autorización la refleja al consultarlo")
    void tipoConAutorizacion() throws Exception {
        when(service.obtenerPorId(2L)).thenReturn(tipo(2L, "Cámara", "Cámara fotográfica", 3, true));

        mockMvc.perform(get(RUTA + "/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereAutorizacion").value(true));
    }

    @Test
    @DisplayName("PUT cambia la duración a 3 días y devuelve el tipo editado")
    void editarReglas() throws Exception {
        var request = new TipoEquipoRequestDto("Multímetro", "Mide voltaje y corriente", 3, false);
        when(service.actualizar(1L, "Multímetro", "Mide voltaje y corriente", 3, false))
                .thenReturn(tipo(1L, "Multímetro", "Mide voltaje y corriente", 3, false));

        mockMvc.perform(put(RUTA + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionMaximaDias").value(3));
    }

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
        mapa.put("requiereAutorizacion", autorizacion);
        return mapa;
    }

    @ParameterizedTest(name = "POST {0}")
    @MethodSource("peticionesInvalidas")
    @DisplayName("POST con datos inválidos responde 400 con el formato común y no llega al servicio")
    void crearConDatosInvalidos(String caso, Map<String, Object> payload, String campo) throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.mensaje").value("Datos de entrada inválidos"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errores." + campo).exists());

        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "PUT {0}")
    @MethodSource("peticionesInvalidas")
    @DisplayName("PUT con datos inválidos responde 400 con el formato común y no llega al servicio")
    void editarConDatosInvalidos(String caso, Map<String, Object> payload, String campo) throws Exception {
        mockMvc.perform(put(RUTA + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errores." + campo).exists());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("un JSON malformado responde 400 con el mensaje del catálogo y sin 'errores'")
    void jsonMalformado() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la petición no es un JSON válido"))
                .andExpect(jsonPath("$.errores").doesNotExist());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("GET de un id inexistente responde 404 con el formato común")
    void consultarInexistente() throws Exception {
        when(service.obtenerPorId(999L))
                .thenThrow(new RecursoNoEncontradoException("Tipo de equipo 999 no encontrado"));

        mockMvc.perform(get(RUTA + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.mensaje", containsString("999")))
                .andExpect(jsonPath("$.errores").doesNotExist());
    }

    @Test
    @DisplayName("PUT de un id inexistente responde 404 con el formato común")
    void editarInexistente() throws Exception {
        when(service.actualizar(eq(999L), anyString(), any(), anyInt(), anyBoolean()))
                .thenThrow(new RecursoNoEncontradoException("Tipo de equipo 999 no encontrado"));

        mockMvc.perform(put(RUTA + "/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TipoEquipoRequestDto("Multímetro", "desc", 3, false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje", containsString("999")));
    }
}
