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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La unidad se guarda en la base (H2 en memoria), se consulta por HTTP y la respuesta se compara con el ejemplo de {@code docs/contrato/openapi.yaml}.
 * Los ids los asigna la base, así que se comparan nombres y tipos de campo, no los valores.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:inventario-contrato-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class UnidadContratoIntegracionTest {

    private static final String RUTA = "/api/v1/inventario-service/unidades";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UnidadRepository unidadRepository;

    @Autowired
    private TipoEquipoRepository tipoEquipoRepository;

    @BeforeEach
    @AfterEach
    void limpiar() {
        unidadRepository.deleteAll();
        tipoEquipoRepository.deleteAll();
    }

    @Test
    @DisplayName("CA-5 (real): una unidad guardada se consulta y su respuesta tiene los campos y tipos del contrato")
    void respuestaRealRespetaElContrato() throws Exception {
        ContratoOpenApi contrato = ContratoOpenApi.cargar();
        TipoEquipo tipo = tipoEquipoRepository.save(TipoEquipo.crear("Multímetro", "Mide voltaje", 7, false));
        Unidad unidad = unidadRepository.save(Unidad.crear(tipo, "MUL-014", EstadoUnidad.DISPONIBLE, "Buen estado"));

        String cuerpo = mockMvc.perform(get(RUTA + "/" + unidad.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode real = objectMapper.readTree(cuerpo);

        // Mismos campos y tipos que el ejemplo. Campos de más (observaciones, tipo.descripcion) no lo rompen.
        contrato.exigirMismosCamposYTipos("$", objectMapper.valueToTree(contrato.ejemploDeUnidad()), real);
        contrato.exigirObligatorios("Unidad", real);
        contrato.exigirObligatorios("TipoEquipo", real.get("tipo"));

        // El estado es uno de los valores del enum del contrato y las reglas del tipo viajan en la misma respuesta.
        assertTrue(contrato.estadosPermitidos().contains(real.get("estado").asText()),
                "El estado " + real.get("estado") + " no está en el enum EstadoUnidad del contrato");
        assertEquals(7, real.get("tipo").get("duracionMaximaDias").asInt());
        assertEquals(false, real.get("tipo").get("requiereAutorizacion").asBoolean());
    }
}
