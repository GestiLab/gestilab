package com.tedesoft.inventarioservice.delivery.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.inventarioservice.domain.services.UnidadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /unidades/{id}}: maneja una sola respuesta con el estado de la unidad y las reglas de su
 * tipo (D-01), tal como lo consume {@code prestamos-service}. La capa web tendrá un servicio simulado.
 *
 * <p>Los CA de este archivo son los de SCRUM-11
 */
@WebMvcTest(UnidadController.class)
class UnidadConsultaContratoTest {

    private static final String RUTA = "/api/v1/inventario-service/unidades";
    private static final Path CONTRATO = Path.of("..", "docs", "contrato", "openapi.yaml");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UnidadService service;

    private static Unidad unidad(long id, String numero, EstadoUnidad estado,
                                 long tipoId, String tipo, int dias, boolean autorizacion) {
        TipoEquipo tipoEquipo = TipoEquipo.crear(tipo, "Descripción de " + tipo, dias, autorizacion);
        ReflectionTestUtils.setField(tipoEquipo, "id", tipoId);
        Unidad unidad = Unidad.crear(tipoEquipo, numero, estado, "Buen estado");
        ReflectionTestUtils.setField(unidad, "id", id);
        return unidad;
    }

    @Test
    @DisplayName("CA-1: unidad MUL-014 disponible; 200 con estado y, en la misma respuesta, las reglas del Multímetro")
    void unidadDisponible() throws Exception {
        when(service.obtenerPorId(14L))
                .thenReturn(unidad(14L, "MUL-014", EstadoUnidad.DISPONIBLE, 1L, "Multímetro", 7, false));

        mockMvc.perform(get(RUTA + "/14"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.id").value(14))
                .andExpect(jsonPath("$.numeroInventario").value("MUL-014"))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.tipo.id").value(1))
                .andExpect(jsonPath("$.tipo.nombre").value("Multímetro"))
                .andExpect(jsonPath("$.tipo.duracionMaximaDias").value(7))
                .andExpect(jsonPath("$.tipo.requiereAutorizacion").value(false));

        // Una sola consulta basta: no hay segunda llamada para traer las reglas del tipo.
        verify(service).obtenerPorId(14L);
        verifyNoMoreInteractions(service);
    }

    @Test
    @DisplayName("CA-2: tipo Cámara; la respuesta muestra requiereAutorizacion en true")
    void tipoConAutorizacion() throws Exception {
        when(service.obtenerPorId(87L))
                .thenReturn(unidad(87L, "CAM-007", EstadoUnidad.DISPONIBLE, 3L, "Cámara", 3, true));

        mockMvc.perform(get(RUTA + "/87"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo.nombre").value("Cámara"))
                .andExpect(jsonPath("$.tipo.duracionMaximaDias").value(3))
                .andExpect(jsonPath("$.tipo.requiereAutorizacion").value(true));
    }

    @Test
    @DisplayName("CA-3: unidad PRESTADA; responde 200 con ese estado (no es un error: decide prestamos-service)")
    void unidadPrestada() throws Exception {
        when(service.obtenerPorId(20L))
                .thenReturn(unidad(20L, "MUL-020", EstadoUnidad.PRESTADA, 1L, "Multímetro", 7, false));

        mockMvc.perform(get(RUTA + "/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PRESTADA"))
                .andExpect(jsonPath("$.tipo.duracionMaximaDias").value(7));
    }

    @Test
    @DisplayName("CA-4: unidad 999 inexistente; 404 con el formato de error común y sin detalles internos")
    void unidadInexistente() throws Exception {
        when(service.obtenerPorId(999L)).thenThrow(new RecursoNoEncontradoException("Unidad 999 no encontrada"));

        mockMvc.perform(get(RUTA + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.mensaje").value("Unidad 999 no encontrada"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errores").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("CA-5: una respuesta real tiene los mismos campos y tipos que el ejemplo de docs/contrato/openapi.yaml")
    @SuppressWarnings("unchecked")
    void respetaElContrato() throws Exception {
        assertTrue(Files.exists(CONTRATO), "No se encontró " + CONTRATO.toAbsolutePath().normalize()
                + ". Las pruebas deben correr desde la carpeta inventario-service dentro del repositorio.");
        Map<String, Object> openapi = leerYaml(CONTRATO);
        Map<String, Object> ejemplo = (Map<String, Object>) en(openapi, "paths", "/unidades/{id}", "get",
                "responses", "200", "content", "application/json", "example");
        Map<String, Object> tipoEjemplo = (Map<String, Object>) ejemplo.get("tipo");

        // Esta unidad simulada se arma con los valores del propio ejemplo: si el contrato cambia, la prueba lo sigue.
        long id = ((Number) ejemplo.get("id")).longValue();
        when(service.obtenerPorId(id)).thenReturn(unidad(id, (String) ejemplo.get("numeroInventario"),
                EstadoUnidad.valueOf((String) ejemplo.get("estado")),
                ((Number) tipoEjemplo.get("id")).longValue(), (String) tipoEjemplo.get("nombre"),
                ((Number) tipoEjemplo.get("duracionMaximaDias")).intValue(),
                (Boolean) tipoEjemplo.get("requiereAutorizacion")));

        String cuerpo = mockMvc.perform(get(RUTA + "/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode real = objectMapper.readTree(cuerpo);

        // 1) Cada campo del ejemplo existe en la respuesta, con el mismo tipo JSON y el mismo valor.
        //    Campos de más (observaciones, tipo.descripcion) no rompen el contrato: son opcionales y quien
        //    consume los ignora.
        compararConEjemplo("$", objectMapper.valueToTree(ejemplo), real);

        // 2) Los campos obligatorios de los esquemas Unidad y TipoEquipo vienen en la respuesta.
        exigirObligatorios("Unidad", openapi, real);
        exigirObligatorios("TipoEquipo", openapi, real.get("tipo"));
    }

    // ---------------------------------------------------------------- utilidades del contrato

    private static Map<String, Object> leerYaml(Path ruta) throws Exception {
        YamlMapFactoryBean yaml = new YamlMapFactoryBean();
        yaml.setResources(new FileSystemResource(ruta));
        yaml.afterPropertiesSet();
        return yaml.getObject();
    }

    @SuppressWarnings("unchecked")
    private static Object en(Map<String, Object> raiz, String... camino) {
        Object actual = raiz;
        for (String clave : camino) {
            assertTrue(actual instanceof Map, "El contrato no tiene la ruta " + String.join(" > ", camino));
            actual = ((Map<String, Object>) actual).get(clave);
            assertNotNull(actual, "El contrato no tiene '" + clave + "' en " + String.join(" > ", camino));
        }
        return actual;
    }

    private static void compararConEjemplo(String ruta, JsonNode esperado, JsonNode real) {
        assertNotNull(real, "Falta el campo " + ruta + " en la respuesta");
        assertEquals(esperado.getNodeType(), real.getNodeType(), "Tipo distinto en " + ruta);
        if (esperado.isObject()) {
            esperado.fieldNames().forEachRemaining(
                    campo -> compararConEjemplo(ruta + "." + campo, esperado.get(campo), real.get(campo)));
        } else {
            assertEquals(esperado, real, "Valor distinto en " + ruta);
        }
    }

    @SuppressWarnings("unchecked")
    private static void exigirObligatorios(String esquema, Map<String, Object> openapi, JsonNode real) {
        List<String> obligatorios = (List<String>) en(openapi, "components", "schemas", esquema, "required");
        for (String campo : obligatorios) {
            assertTrue(real.has(campo), "Falta el campo obligatorio '" + campo + "' del esquema " + esquema);
        }
    }
}
