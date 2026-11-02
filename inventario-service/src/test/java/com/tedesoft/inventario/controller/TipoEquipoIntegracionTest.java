package com.tedesoft.inventario.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tedesoft.inventario.domain.TipoEquipo;
import com.tedesoft.inventario.dto.TipoEquipoRequestDto;
import com.tedesoft.inventario.repository.TipoEquipoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo contra H2 con Flyway: verifica también que el DDL de la migración coincide con la
 * entidad (ddl-auto=validate) y que lo inválido no se persiste. No carga los datos iniciales de desarrollo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:inventario-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.flyway.locations=classpath:db/migration"
})
class TipoEquipoIntegracionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TipoEquipoRepository repository;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
    }

    private String crear(TipoEquipoRequestDto request) throws Exception {
        return mockMvc.perform(post("/tipos-equipo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    @Test
    @DisplayName("CA-1: crear Multímetro y consultarlo devuelve exactamente lo guardado")
    void crearYConsultar() throws Exception {
        String location = crear(new TipoEquipoRequestDto("Multímetro", "Mide voltaje y corriente", 7, false));

        mockMvc.perform(get(URI.create(location)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Multímetro"))
                .andExpect(jsonPath("$.descripcion").value("Mide voltaje y corriente"))
                .andExpect(jsonPath("$.duracionMaximaDias").value(7))
                .andExpect(jsonPath("$.requiereAutorizacionPrevia").value(false));
    }

    @Test
    @DisplayName("CA-2: la autorización previa 'sí' queda persistida y se devuelve")
    void conAutorizacionPrevia() throws Exception {
        String location = crear(new TipoEquipoRequestDto("Cámara", "Cámara fotográfica", 3, true));

        mockMvc.perform(get(URI.create(location)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereAutorizacionPrevia").value(true));
    }

    @Test
    @DisplayName("CA-3: editar la duración de 7 a 3 días se refleja en consultas posteriores")
    void editarReglas() throws Exception {
        String location = crear(new TipoEquipoRequestDto("Multímetro", "desc", 7, false));

        mockMvc.perform(put(URI.create(location))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TipoEquipoRequestDto("Multímetro", "desc", 3, false))))
                .andExpect(status().isOk());

        mockMvc.perform(get(URI.create(location)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionMaximaDias").value(3));
    }

    @Test
    @DisplayName("CA-4: datos inválidos responden 400 y no guardan ni modifican nada")
    void datosInvalidosNoSePersisten() throws Exception {
        String location = crear(new TipoEquipoRequestDto("Multímetro", "desc", 7, false));
        long antes = repository.count();

        mockMvc.perform(post("/tipos-equipo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("descripcion", "sin nombre", "duracionMaximaDias", 5,
                                        "requiereAutorizacionPrevia", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));

        mockMvc.perform(put(URI.create(location))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TipoEquipoRequestDto("Multímetro", "desc", 0, false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("duracionMaximaDias"));

        assertThat(repository.count()).isEqualTo(antes);
        mockMvc.perform(get(URI.create(location)))
                .andExpect(jsonPath("$.duracionMaximaDias").value(7));
    }

    @Test
    @DisplayName("CA-5: consultar o editar el id 999 responde 404 con el formato común")
    void tipoInexistente() throws Exception {
        mockMvc.perform(get("/tipos-equipo/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.ruta").value("/tipos-equipo/999"));

        mockMvc.perform(put("/tipos-equipo/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TipoEquipoRequestDto("Multímetro", "desc", 3, false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("CA-6: un tipo nuevo creado por POST queda operativo con sus reglas sin tocar código")
    void tipoNuevoQuedaOperativo() throws Exception {
        String location = crear(new TipoEquipoRequestDto("Osciloscopio", "Equipo de laboratorio", 14, true));
        long id = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        TipoEquipo tipo = repository.findById(id).orElseThrow();

        assertThat(tipo.isRequiereAutorizacionPrevia()).isTrue();
        assertThat(tipo.calcularFechaDevolucion(LocalDate.of(2026, 11, 2)))
                .isEqualTo(LocalDate.of(2026, 11, 16));
    }
}
