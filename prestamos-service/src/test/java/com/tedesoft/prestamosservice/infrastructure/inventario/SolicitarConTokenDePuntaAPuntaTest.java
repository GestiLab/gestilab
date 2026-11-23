package com.tedesoft.prestamosservice.infrastructure.inventario;

import com.tedesoft.prestamosservice.delivery.security.ConSeguridadDePrueba;
import com.tedesoft.prestamosservice.delivery.security.TokensDePrueba;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-24 de punta a punta dentro del proceso: petición HTTP con un token firmado → seguridad real → controlador →
 * servicio → JPA sobre H2 → {@link InventarioHttpClient} real (con la configuración de producción) → un servidor
 * HTTP local en lugar de inventario. Comprueba que el token que llega a préstamos es el mismo que recibe inventario
 * (CA-3) y que el préstamo queda a nombre del {@code sub} (CA-2). No usa servicios externos (CA-9).
 */
@ConSeguridadDePrueba
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SCRUM-24 de punta a punta: solicitar reenvía a inventario el mismo token")
class SolicitarConTokenDePuntaAPuntaTest {

    private static final String RUTA = "/api/v1/prestamos-service/prestamos";

    /** Se arranca antes que el contexto: la URL de inventario debe existir cuando se crea el RestClient. */
    private static final InventarioStub INVENTARIO = iniciarInventario();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IPrestamoDao prestamoDao;

    private static InventarioStub iniciarInventario() {
        try {
            return InventarioStub.iniciar();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @DynamicPropertySource
    static void apuntarAlInventarioDePrueba(DynamicPropertyRegistry propiedades) {
        propiedades.add("inventario.url", INVENTARIO::url);
    }

    @AfterAll
    static void detenerInventario() {
        INVENTARIO.close();
    }

    @BeforeEach
    @AfterEach
    void limpiarBase() {
        // La base H2 en memoria se comparte con otras clases de prueba
        prestamoDao.deleteAll();
    }

    @Test
    @DisplayName("CA-2 y CA-3: el usuario solicita; inventario recibe su token y el préstamo queda a nombre de su sub")
    void solicitar_reenviaElMismoTokenAInventario_yGuardaElSub() throws Exception {
        String token = TokensDePrueba.valido("ana", "usuario");

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\": " + InventarioStub.ID_MULTIMETRO + "}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unidadId").value(InventarioStub.ID_MULTIMETRO))
                .andExpect(jsonPath("$.solicitanteId").value("ana"));

        assertThat(INVENTARIO.ultimaAutorizacion()).isEqualTo("Bearer " + token);
        assertThat(prestamoDao.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("CA-1 de punta a punta: lo que solicitó cada usuario solo aparece en su propia lista")
    void cadaUsuarioVeSoloLoQueSolicito() throws Exception {
        String ana = TokensDePrueba.valido("ana", "usuario");
        String luis = TokensDePrueba.valido("luis", "usuario");

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\": " + InventarioStub.ID_MULTIMETRO + "}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ana))
                .andExpect(status().isCreated());
        assertThat(INVENTARIO.ultimaAutorizacion()).isEqualTo("Bearer " + ana);

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadId\": " + InventarioStub.ID_CAMARA + "}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + luis))
                .andExpect(status().isCreated());
        assertThat(INVENTARIO.ultimaAutorizacion()).isEqualTo("Bearer " + luis);

        mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].solicitanteId").value("ana"));

        mockMvc.perform(get(RUTA).param("persona", "ana").header(HttpHeaders.AUTHORIZATION, "Bearer " + luis))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.auxiliar()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
