package com.tedesoft.inventarioservice.delivery.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tedesoft.inventarioservice.delivery.rest.dto.UnidadRequestDto;
import com.tedesoft.inventarioservice.domain.entities.EstadoUnidad;
import com.tedesoft.inventarioservice.domain.entities.TipoEquipo;
import com.tedesoft.inventarioservice.domain.entities.Unidad;
import com.tedesoft.inventarioservice.domain.repositories.TipoEquipoRepository;
import com.tedesoft.inventarioservice.domain.repositories.UnidadRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo contra H2 en memoria. Verifica sobre todo lo que los simulados no pueden: que el
 * número duplicado responde 409 (y no una excepción de base de datos), que un 400 o un 404 no guardan
 * nada y que el estado se persiste y se lee de nuevo.
 *
 * <p>Usa su propia base H2 (no la de TipoEquipoIntegracionTest) y limpia al terminar: las unidades
 * referencian tipos, así que los tipos no se pueden borrar mientras queden unidades.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:inventario-unidades-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class UnidadIntegracionTest {

    private static final String RUTA = "/api/v1/inventario-service/unidades";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UnidadRepository unidadRepository;

    @Autowired
    private TipoEquipoRepository tipoEquipoRepository;

    private TipoEquipo multimetro;

    @BeforeEach
    void preparar() {
        limpiar();
        multimetro = tipoEquipoRepository.save(TipoEquipo.crear("Multímetro", "Mide voltaje", 7, false));
    }

    @AfterEach
    void limpiar() {
        unidadRepository.deleteAll();
        tipoEquipoRepository.deleteAll();
    }

    private String crear(UnidadRequestDto request) throws Exception {
        return mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    @Test
    @DisplayName("CA-1/CA-6: crear MUL-014 y consultarla devuelve número, estado, observaciones y tipo con sus reglas")
    void crearYConsultar() throws Exception {
        String location = crear(new UnidadRequestDto("MUL-014", "DISPONIBLE", "Buen estado", multimetro.getId()));

        mockMvc.perform(get(URI.create(location)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroInventario").value("MUL-014"))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.observaciones").value("Buen estado"))
                .andExpect(jsonPath("$.tipo.id").value(multimetro.getId()))
                .andExpect(jsonPath("$.tipo.nombre").value("Multímetro"))
                .andExpect(jsonPath("$.tipo.duracionMaximaDias").value(7))
                .andExpect(jsonPath("$.tipo.requiereAutorizacion").value(false));
    }

    @Test
    @DisplayName("CA-2: un número repetido responde 409 con mensaje claro, sin 500 y sin guardar otra unidad")
    void numeroRepetido() throws Exception {
        crear(new UnidadRequestDto("MUL-014", "DISPONIBLE", null, multimetro.getId()));

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-014", "PRESTADA", "otra", multimetro.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensaje", containsString("MUL-014")));

        assertThat(unidadRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("CA-2: el número se compara sin distinguir mayúsculas ni espacios laterales")
    void numeroRepetidoConOtraEscritura() throws Exception {
        crear(new UnidadRequestDto("MUL-014", "DISPONIBLE", null, multimetro.getId()));

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("  mul-014 ", "DISPONIBLE", null, multimetro.getId()))))
                .andExpect(status().isConflict());

        assertThat(unidadRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("CA-2: editar una unidad con el número de otra responde 409 y no la modifica")
    void editarConNumeroDeOtra() throws Exception {
        crear(new UnidadRequestDto("MUL-014", "DISPONIBLE", null, multimetro.getId()));
        String location = crear(new UnidadRequestDto("MUL-015", "DISPONIBLE", null, multimetro.getId()));

        mockMvc.perform(put(URI.create(location))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-014", "PRESTADA", null, multimetro.getId()))))
                .andExpect(status().isConflict());

        mockMvc.perform(get(URI.create(location)))
                .andExpect(jsonPath("$.numeroInventario").value("MUL-015"))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"));
    }

    @Test
    @DisplayName("CA-3: editar al estado EN_REVISION se persiste y la consulta posterior lo refleja")
    void editarAEnRevision() throws Exception {
        String location = crear(new UnidadRequestDto("MUL-014", "DISPONIBLE", null, multimetro.getId()));

        mockMvc.perform(put(URI.create(location))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-014", "EN_REVISION", "Calibración", multimetro.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));

        mockMvc.perform(get(URI.create(location)))
                .andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.observaciones").value("Calibración"));

        Unidad guardada = unidadRepository.findAll().get(0);
        assertThat(guardada.getEstado()).isEqualTo(EstadoUnidad.EN_REVISION);
    }

    @Test
    @DisplayName("CA-4: un estado no permitido responde 400 con los válidos y no guarda ni modifica nada")
    void estadoInvalido() throws Exception {
        String location = crear(new UnidadRequestDto("MUL-014", "DISPONIBLE", null, multimetro.getId()));
        long antes = unidadRepository.count();

        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-020", "perdida", null, multimetro.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.estado", containsString("EN_REVISION")));

        mockMvc.perform(put(URI.create(location))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-014", "perdida", null, multimetro.getId()))))
                .andExpect(status().isBadRequest());

        assertThat(unidadRepository.count()).isEqualTo(antes);
        mockMvc.perform(get(URI.create(location)))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"));
    }

    @Test
    @DisplayName("CA-5: crear con un tipo inexistente (999) responde 404 y no guarda nada")
    void tipoInexistente() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-014", "DISPONIBLE", null, 999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje", containsString("999")));

        assertThat(unidadRepository.count()).isZero();
    }

    @Test
    @DisplayName("CA-6: consultar o editar la unidad 999 responde 404 con el formato común")
    void unidadInexistente() throws Exception {
        mockMvc.perform(get(RUTA + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje").value("Unidad 999 no encontrada"));

        mockMvc.perform(put(RUTA + "/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UnidadRequestDto("MUL-014", "DISPONIBLE", null, multimetro.getId()))))
                .andExpect(status().isNotFound());
    }
}
