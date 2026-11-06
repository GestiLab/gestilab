package com.tedesoft.inventarioservice.delivery.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tedesoft.inventarioservice.delivery.rest.dto.UnidadRequestDto;
import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.inventarioservice.domain.exception.ReglaNegocioException;
import com.tedesoft.inventarioservice.domain.services.UnidadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
@WebMvcTest(UnidadController.class)
class UnidadControllerTest {

    private static final String RUTA = "/api/v1/inventario-service/unidades";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UnidadService service;

    private static TipoEquipo multimetro() {
        TipoEquipo tipo = TipoEquipo.crear("Multímetro", "Mide voltaje", 7, false);
        ReflectionTestUtils.setField(tipo, "id", 1L);
        return tipo;
    }

    private static Unidad unidad(long id, String numero, EstadoUnidad estado, String observaciones) {
        Unidad unidad = Unidad.crear(multimetro(), numero, estado, observaciones);
        ReflectionTestUtils.setField(unidad, "id", id);
        return unidad;
    }

    // ---------------------------------------------------------------- CA-1 y CA-6

    @Test
    @DisplayName("CA-1: POST crea la unidad con 201, Location y todos sus datos; el GET los devuelve")
    void crearUnidad() throws Exception {
        var request = new UnidadRequestDto("MUL-014", "DISPONIBLE", "Buen estado", 1L);
        Unidad creada = unidad(10L, "MUL-014", EstadoUnidad.DISPONIBLE, "Buen estado");
        when(service.crear(1L, "MUL-014", EstadoUnidad.DISPONIBLE, "Buen estado")).thenReturn(creada);
        when(service.obtenerPorId(10L)).thenReturn(creada);

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(RUTA + "/10")))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.numeroInventario").value("MUL-014"))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.observaciones").value("Buen estado"))
                .andExpect(jsonPath("$.tipo.id").value(1))
                .andExpect(jsonPath("$.tipo.nombre").value("Multímetro"));

        mockMvc.perform(get(RUTA + "/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroInventario").value("MUL-014"))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.observaciones").value("Buen estado"))
                .andExpect(jsonPath("$.tipo.duracionMaximaDias").value(7))
                .andExpect(jsonPath("$.tipo.requiereAutorizacion").value(false));
    }

    @Test
    @DisplayName("CA-6: GET de un id inexistente responde 404 con el formato común")
    void consultarInexistente() throws Exception {
        when(service.obtenerPorId(999L)).thenThrow(new RecursoNoEncontradoException("Unidad 999 no encontrada"));

        mockMvc.perform(get(RUTA + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.mensaje").value("Unidad 999 no encontrada"));
    }

    // ---------------------------------------------------------------- CA-2

    @Test
    @DisplayName("CA-2: un número de inventario repetido responde 409 con mensaje claro")
    void numeroRepetido() throws Exception {
        when(service.crear(eq(1L), eq("MUL-014"), any(), any())).thenThrow(new ReglaNegocioException(
                "Ya existe una unidad con el número de inventario \"MUL-014\""));

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-014", "DISPONIBLE", null, 1L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.mensaje", containsString("MUL-014")));
    }

    // ---------------------------------------------------------------- CA-3

    @Test
    @DisplayName("CA-3: PUT al estado EN_REVISION lo refleja en la respuesta y en la consulta posterior")
    void editarAEnRevision() throws Exception {
        Unidad editada = unidad(10L, "MUL-014", EstadoUnidad.EN_REVISION, "Calibración");
        when(service.actualizar(10L, 1L, "MUL-014", EstadoUnidad.EN_REVISION, "Calibración")).thenReturn(editada);
        when(service.obtenerPorId(10L)).thenReturn(editada);

        mockMvc.perform(put(RUTA + "/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-014", "EN_REVISION", "Calibración", 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));

        mockMvc.perform(get(RUTA + "/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));
    }

    @Test
    @DisplayName("el estado se acepta también en minúsculas y siempre se responde en su forma canónica")
    void estadoEnMinusculas() throws Exception {
        when(service.crear(1L, "MUL-015", EstadoUnidad.EN_REVISION, null))
                .thenReturn(unidad(11L, "MUL-015", EstadoUnidad.EN_REVISION, null));

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-015", "en_revision", null, 1L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));
    }

    // ---------------------------------------------------------------- CA-4

    @ParameterizedTest(name = "estado \"{0}\"")
    @ValueSource(strings = {"perdida", "en revisión", "", "   "})
    @DisplayName("CA-4: crear con un estado no permitido responde 400 y lista los estados válidos")
    void crearConEstadoInvalido(String estado) throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-014", estado, null, 1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.errores.estado", containsString("DISPONIBLE")))
                .andExpect(jsonPath("$.errores.estado", containsString("PRESTADA")))
                .andExpect(jsonPath("$.errores.estado", containsString("EN_REVISION")))
                .andExpect(jsonPath("$.errores.estado", containsString("FUERA_DE_SERVICIO")));

        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "estado \"{0}\"")
    @ValueSource(strings = {"perdida", "en revisión"})
    @DisplayName("CA-4: editar con un estado no permitido responde 400 y no llega al servicio")
    void editarConEstadoInvalido(String estado) throws Exception {
        mockMvc.perform(put(RUTA + "/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-014", estado, null, 1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.estado", containsString("FUERA_DE_SERVICIO")));

        verifyNoInteractions(service);
    }

    static Stream<Arguments> camposFaltantes() {
        return Stream.of(
                Arguments.of("sin número de inventario", cuerpo(null, "DISPONIBLE", 1), "numeroInventario"),
                Arguments.of("número en blanco", cuerpo("  ", "DISPONIBLE", 1), "numeroInventario"),
                Arguments.of("sin estado", cuerpo("MUL-014", null, 1), "estado"),
                Arguments.of("sin tipo de equipo", cuerpo("MUL-014", "DISPONIBLE", null), "tipoEquipoId"),
                Arguments.of("tipo de equipo cero", cuerpo("MUL-014", "DISPONIBLE", 0), "tipoEquipoId")
        );
    }

    /** Arma el JSON como mapa para poder omitir campos (un record no permite "faltantes"). */
    private static Map<String, Object> cuerpo(String numero, String estado, Integer tipoId) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        if (numero != null) {
            mapa.put("numeroInventario", numero);
        }
        if (estado != null) {
            mapa.put("estado", estado);
        }
        if (tipoId != null) {
            mapa.put("tipoEquipoId", tipoId);
        }
        return mapa;
    }

    @ParameterizedTest(name = "POST {0}")
    @MethodSource("camposFaltantes")
    @DisplayName("CA-4: crear con campos obligatorios inválidos responde 400 indicando el campo")
    void crearConCamposInvalidos(String caso, Map<String, Object> payload, String campo) throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores." + campo).exists());

        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "PUT {0}")
    @MethodSource("camposFaltantes")
    @DisplayName("CA-4: editar con campos obligatorios inválidos responde 400 indicando el campo")
    void editarConCamposInvalidos(String caso, Map<String, Object> payload, String campo) throws Exception {
        mockMvc.perform(put(RUTA + "/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores." + campo).exists());

        verifyNoInteractions(service);
    }

    // ---------------------------------------------------------------- CA-5

    @Test
    @DisplayName("CA-5: crear con un tipo inexistente responde 404 con el formato común")
    void tipoInexistente() throws Exception {
        when(service.crear(eq(999L), anyString(), any(), any()))
                .thenThrow(new RecursoNoEncontradoException("Tipo de equipo 999 no encontrado"));

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-014", "DISPONIBLE", null, 999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje", containsString("999")));
    }

    @Test
    @DisplayName("editar una unidad inexistente responde 404 con el formato común")
    void editarInexistente() throws Exception {
        when(service.actualizar(eq(999L), eq(1L), anyString(), any(), any()))
                .thenThrow(new RecursoNoEncontradoException("Unidad 999 no encontrada"));

        mockMvc.perform(put(RUTA + "/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-014", "DISPONIBLE", null, 1L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Unidad 999 no encontrada"));
    }

    // ---------------------------------------------------------------- Ajustes de la revisión del PR #6

    static Stream<Arguments> cuerposQueIncumplenElContrato() {
        return Stream.of(
                Arguments.of("tipoEquipoId decimal: no se trunca a otro tipo",
                        "{\"numeroInventario\":\"MUL-901\",\"estado\":\"DISPONIBLE\",\"tipoEquipoId\":1.9}"),
                Arguments.of("tipoEquipoId como texto",
                        "{\"numeroInventario\":\"MUL-902\",\"estado\":\"DISPONIBLE\",\"tipoEquipoId\":\"1\"}"),
                Arguments.of("basura después del JSON",
                        "{\"numeroInventario\":\"MUL-903\",\"estado\":\"DISPONIBLE\",\"tipoEquipoId\":1} xxx"),
                Arguments.of("número de inventario solo con espacio Unicode (U+2003)",
                        "{\"numeroInventario\":\"\\u2003\",\"estado\":\"DISPONIBLE\",\"tipoEquipoId\":1}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cuerposQueIncumplenElContrato")
    @DisplayName("un cuerpo que no cumple el contrato responde 400 y no llega al servicio")
    void cuerpoQueIncumpleElContrato(String caso, String cuerpo) throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("un Accept que no es JSON responde 406 SIN crear la unidad")
    void acceptNoJsonNoCreaLaUnidad() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-014", "DISPONIBLE", null, 1L))))
                .andExpect(status().isNotAcceptable());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("el largo máximo se mide sin los espacios laterales: 50 caracteres rodeados de espacios se aceptan")
    void numeroEnElLimiteConEspacios() throws Exception {
        String numero = "A".repeat(50);
        when(service.crear(1L, numero, EstadoUnidad.DISPONIBLE, null))
                .thenReturn(unidad(12L, numero, EstadoUnidad.DISPONIBLE, null));

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("  " + numero + "  ", "DISPONIBLE", null, 1L))))
                .andExpect(status().isCreated());
    }
}
