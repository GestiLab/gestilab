package com.tedesoft.inventarioservice.delivery.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El catálogo completo contra H2: 120 unidades (40 por tipo) y las consultas reales con filtros, orden,
 * escape de comodines y errores. Usa las mismas anotaciones que UnidadIntegracionTest para compartir su
 * contexto de Spring y su base H2; cada prueba empieza y termina con la base limpia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:inventario-unidades-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class UnidadCatalogoIntegracionTest {

    private static final String UNIDADES = "/api/v1/inventario-service/unidades";
    private static final String TIPOS = "/api/v1/inventario-service/tipos-equipo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UnidadRepository unidadRepository;

    @Autowired
    private TipoEquipoRepository tipoEquipoRepository;

    private TipoEquipo multimetro;
    private TipoEquipo portatil;
    private TipoEquipo camara;

    /** Por tipo: 30 disponibles, 4 prestadas, 3 en revisión y 3 fuera de servicio (120 en total). */
    @BeforeEach
    void sembrar() {
        limpiar();
        multimetro = tipoEquipoRepository.save(TipoEquipo.crear("Multímetro", "Mide voltaje", 7, false));
        portatil = tipoEquipoRepository.save(TipoEquipo.crear("Portátil", "Computador portátil", 1, false));
        camara = tipoEquipoRepository.save(TipoEquipo.crear("Cámara", "Cámara fotográfica", 3, true));
        sembrarUnidades(multimetro, "MUL");
        sembrarUnidades(portatil, "POR");
        sembrarUnidades(camara, "CAM");
    }

    @AfterEach
    void limpiar() {
        unidadRepository.deleteAll();
        tipoEquipoRepository.deleteAll();
    }

    private void sembrarUnidades(TipoEquipo tipo, String prefijo) {
        List<Unidad> unidades = new ArrayList<>();
        for (int n = 1; n <= 40; n++) {
            EstadoUnidad estado = n <= 30 ? EstadoUnidad.DISPONIBLE
                    : n <= 34 ? EstadoUnidad.PRESTADA
                    : n <= 37 ? EstadoUnidad.EN_REVISION
                    : EstadoUnidad.FUERA_DE_SERVICIO;
            unidades.add(Unidad.crear(tipo, String.format("%s-%03d", prefijo, n), estado, null));
        }
        unidadRepository.saveAll(unidades);
    }

    /** GET del catálogo con los parámetros dados como pares clave, valor; devuelve el JSON de la página. */
    private JsonNode consultar(String... paresClaveValor) throws Exception {
        MockHttpServletRequestBuilder peticion = get(UNIDADES);
        for (int i = 0; i < paresClaveValor.length; i += 2) {
            peticion.param(paresClaveValor[i], paresClaveValor[i + 1]);
        }
        String cuerpo = mockMvc.perform(peticion)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(cuerpo);
    }

    // ---------------------------------------------------------------- CA-1, CA-6, CA-10

    @Test
    @DisplayName("CA-1: sin parámetros devuelve la página 0 con 20 de las 120 unidades y 6 páginas")
    void primeraPaginaPorDefecto() throws Exception {
        JsonNode pagina = consultar();

        assertThat(pagina.get("content").size()).isEqualTo(20);
        assertThat(pagina.get("number").asInt()).isZero();
        assertThat(pagina.get("size").asInt()).isEqualTo(20);
        assertThat(pagina.get("totalElements").asLong()).isEqualTo(120);
        assertThat(pagina.get("totalPages").asInt()).isEqualTo(6);
    }

    @Test
    @DisplayName("CA-1/CA-10: recorrer las 6 páginas no repite ni pierde ninguna unidad y respeta el orden por id")
    void recorreLasSeisPaginas() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int pagina = 0; pagina < 6; pagina++) {
            JsonNode respuesta = consultar("page", String.valueOf(pagina), "size", "20");
            assertThat(respuesta.get("content").size()).isEqualTo(20);
            respuesta.get("content").forEach(unidad -> ids.add(unidad.get("id").asLong()));
        }

        assertThat(ids).hasSize(120).doesNotHaveDuplicates().isSorted();
    }

    @Test
    @DisplayName("CA-6: la página 50 responde 200 con content vacío y el total real")
    void paginaFueraDeRango() throws Exception {
        JsonNode pagina = consultar("page", "50");

        assertThat(pagina.get("content").size()).isZero();
        assertThat(pagina.get("number").asInt()).isEqualTo(50);
        assertThat(pagina.get("totalElements").asLong()).isEqualTo(120);
        assertThat(pagina.get("totalPages").asInt()).isEqualTo(6);
    }

    // ---------------------------------------------------------------- CA-2 a CA-5

    @Test
    @DisplayName("CA-2: filtrar por tipoId devuelve solo las unidades de ese tipo")
    void filtraPorTipo() throws Exception {
        JsonNode pagina = consultar("tipoId", String.valueOf(camara.getId()), "size", "100");

        assertThat(pagina.get("totalElements").asLong()).isEqualTo(40);
        pagina.get("content").forEach(unidad ->
                assertThat(unidad.get("tipo").get("id").asLong()).isEqualTo(camara.getId()));
    }

    @Test
    @DisplayName("CA-3: estado=DISPONIBLE excluye las unidades en los otros tres estados")
    void soloDisponibles() throws Exception {
        JsonNode pagina = consultar("estado", "DISPONIBLE", "size", "100");

        assertThat(pagina.get("totalElements").asLong()).isEqualTo(90);
        pagina.get("content").forEach(unidad -> assertThat(unidad.get("estado").asText()).isEqualTo("DISPONIBLE"));
        assertThat(consultar("estado", "PRESTADA").get("totalElements").asLong()).isEqualTo(12);
        assertThat(consultar("estado", "EN_REVISION").get("totalElements").asLong()).isEqualTo(9);
        assertThat(consultar("estado", "FUERA_DE_SERVICIO").get("totalElements").asLong()).isEqualTo(9);
        assertThat(consultar("estado", "disponible").get("totalElements").asLong()).isEqualTo(90);
    }

    @Test
    @DisplayName("CA-4: buscar por nombre del tipo es parcial y no distingue mayúsculas")
    void buscaPorNombreDelTipo() throws Exception {
        assertThat(consultar("nombre", "mult").get("totalElements").asLong()).isEqualTo(40);
        assertThat(consultar("nombre", "MULT").get("totalElements").asLong()).isEqualTo(40);
        assertThat(consultar("nombre", "MuLt").get("totalElements").asLong()).isEqualTo(40);
        assertThat(consultar("nombre", "ámar").get("totalElements").asLong()).isEqualTo(40);
        assertThat(consultar("nombre", "  port  ").get("totalElements").asLong()).isEqualTo(40);
        assertThat(consultar("nombre", "inexistente").get("totalElements").asLong()).isZero();
        assertThat(consultar("nombre", "   ").get("totalElements").asLong()).isEqualTo(120);
    }

    @Test
    @DisplayName("CA-4: buscar \"multi\" encuentra \"Multímetro\": la búsqueda no distingue tildes")
    void buscaSinDistinguirTildes() throws Exception {
        assertThat(consultar("nombre", "multi").get("totalElements").asLong()).isEqualTo(40);
        assertThat(consultar("nombre", "MULTIMETRO").get("totalElements").asLong()).isEqualTo(40);
        assertThat(consultar("nombre", "camara").get("totalElements").asLong()).isEqualTo(40);
        assertThat(consultar("nombre", "portatil").get("totalElements").asLong()).isEqualTo(40);
        assertThat(consultar("nombre", "PORTÁTIL").get("totalElements").asLong()).isEqualTo(40);
        // Combinada con los otros filtros sigue siendo AND
        assertThat(consultar("nombre", "camara", "estado", "PRESTADA").get("totalElements").asLong())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("un carácter NUL en la búsqueda responde 200 sin resultados, no un error")
    void busquedaConCaracterNul() throws Exception {
        assertThat(consultar("nombre", "mul\u0000ti").get("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("CA-5: los filtros se combinan con AND")
    void combinaFiltros() throws Exception {
        assertThat(consultar("tipoId", String.valueOf(multimetro.getId()), "estado", "PRESTADA")
                .get("totalElements").asLong()).isEqualTo(4);
        assertThat(consultar("tipoId", String.valueOf(multimetro.getId()), "nombre", "cámara")
                .get("totalElements").asLong()).isZero();
        assertThat(consultar("estado", "EN_REVISION", "nombre", "port")
                .get("totalElements").asLong()).isEqualTo(3);
        assertThat(consultar("tipoId", String.valueOf(portatil.getId()), "estado", "DISPONIBLE", "nombre", "PORT")
                .get("totalElements").asLong()).isEqualTo(30);
    }

    // ---------------------------------------------------------------- CA-9

    @Test
    @DisplayName("CA-9: % y _ en nombre se buscan como texto literal, no como comodines")
    void comodinesSeBuscanComoTexto() throws Exception {
        TipoEquipo conPorciento = tipoEquipoRepository.save(TipoEquipo.crear("Descuento 50% nuevo", null, 2, false));
        TipoEquipo sinPorciento = tipoEquipoRepository.save(TipoEquipo.crear("Descuento 50 nuevo", null, 2, false));
        TipoEquipo conGuion = tipoEquipoRepository.save(TipoEquipo.crear("Sensor_A", null, 2, false));
        TipoEquipo sinGuion = tipoEquipoRepository.save(TipoEquipo.crear("SensorXA", null, 2, false));
        unidadRepository.save(Unidad.crear(conPorciento, "EXT-001", EstadoUnidad.DISPONIBLE, null));
        unidadRepository.save(Unidad.crear(sinPorciento, "EXT-002", EstadoUnidad.DISPONIBLE, null));
        unidadRepository.save(Unidad.crear(conGuion, "EXT-003", EstadoUnidad.DISPONIBLE, null));
        unidadRepository.save(Unidad.crear(sinGuion, "EXT-004", EstadoUnidad.DISPONIBLE, null));

        JsonNode porciento = consultar("nombre", "50%");
        assertThat(porciento.get("totalElements").asLong()).isEqualTo(1);
        assertThat(porciento.get("content").get(0).get("tipo").get("nombre").asText()).isEqualTo("Descuento 50% nuevo");

        JsonNode guion = consultar("nombre", "sensor_");
        assertThat(guion.get("totalElements").asLong()).isEqualTo(1);
        assertThat(guion.get("content").get(0).get("tipo").get("nombre").asText()).isEqualTo("Sensor_A");

        // Un % o un _ solos NO equivalen a "todo": solo coincide el tipo que contiene ese carácter
        assertThat(consultar("nombre", "%").get("totalElements").asLong()).isEqualTo(1);
        assertThat(consultar("nombre", "_").get("totalElements").asLong()).isEqualTo(1);
        // La barra de escape tampoco rompe la consulta
        assertThat(consultar("nombre", "\\").get("totalElements").asLong()).isZero();
    }

    // ---------------------------------------------------------------- CA-8

    @Test
    @DisplayName("CA-8: page=-1 responde 400 con el formato común e indica el campo page")
    void paginaNegativa() throws Exception {
        mockMvc.perform(get(UNIDADES).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.errores.page").exists());
    }

    @Test
    @DisplayName("CA-8: size=0 y size negativo responden 400 e indican el campo size")
    void tamanoInvalido() throws Exception {
        mockMvc.perform(get(UNIDADES).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.size").exists());
        mockMvc.perform(get(UNIDADES).param("size", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.size").exists());
    }

    @Test
    @DisplayName("CA-8: size mayor que 100 responde 400 e indica el campo size; 100 se acepta")
    void tamanoMayorQueElTope() throws Exception {
        mockMvc.perform(get(UNIDADES).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.size", containsString("100")));
        assertThat(consultar("size", "100").get("content").size()).isEqualTo(100);
    }

    @Test
    @DisplayName("CA-6: una página enorme (desborda el desplazamiento) responde 200 vacía, no 500")
    void paginaEnorme() throws Exception {
        JsonNode pagina = consultar("page", String.valueOf(Integer.MAX_VALUE), "size", "20");

        assertThat(pagina.get("content").size()).isZero();
        assertThat(pagina.get("totalElements").asLong()).isEqualTo(120);
        assertThat(pagina.get("totalPages").asInt()).isEqualTo(6);
    }

    @Test
    @DisplayName("un estado desconocido responde 400 y lista los estados permitidos")
    void estadoDesconocido() throws Exception {
        mockMvc.perform(get(UNIDADES).param("estado", "perdida"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.estado", containsString("EN_REVISION")));
    }

    // ---------------------------------------------------------------- CA-12

    @Test
    @DisplayName("CA-12: GET /tipos-equipo devuelve la lista completa con las reglas de cada tipo, ordenada por id")
    void listaDeTipos() throws Exception {
        mockMvc.perform(get(TIPOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(multimetro.getId()))
                .andExpect(jsonPath("$[0].nombre").value("Multímetro"))
                .andExpect(jsonPath("$[0].duracionMaximaDias").value(7))
                .andExpect(jsonPath("$[0].requiereAutorizacion").value(false))
                .andExpect(jsonPath("$[1].nombre").value("Portátil"))
                .andExpect(jsonPath("$[1].duracionMaximaDias").value(1))
                .andExpect(jsonPath("$[2].nombre").value("Cámara"))
                .andExpect(jsonPath("$[2].requiereAutorizacion").value(true));
    }
}
