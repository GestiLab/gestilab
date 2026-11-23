package com.tedesoft.prestamosservice.delivery.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tedesoft.prestamosservice.delivery.rest.PrestamoRestController;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-20: con el decodificador REAL (no el de pruebas) y Keycloak inalcanzable, un token con forma válida no se
 * puede comprobar. El servicio debe responder 401 con el formato común (como dicen el README y el PR) y no dejar
 * escapar la excepción como un 500.
 */
@WebMvcTest(PrestamoRestController.class)
@Import(SeguridadConfig.class)
@TestPropertySource(properties = {
        // el puerto 9 rechaza la conexión al instante: Keycloak "caído"
        "gestilab.seguridad.jwk-set-uri=http://127.0.0.1:9/realms/gestilab/protocol/openid-connect/certs",
        "gestilab.seguridad.issuer=" + TokensDePrueba.EMISOR})
@DisplayName("SCRUM-20: Keycloak caído en prestamos-service")
class AutorizacionKeycloakCaidoTest {

    private static final String RUTA = "/api/v1/prestamos-service/prestamos";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPrestamoService prestamoService;

    private ListAppender<ILoggingEvent> lineas;
    private Logger registro;

    @BeforeEach
    void capturarRegistro() {
        registro = (Logger) LoggerFactory.getLogger(RegistroAccesoRechazado.NOMBRE_REGISTRO);
        lineas = new ListAppender<>();
        lineas.start();
        registro.addAppender(lineas);
    }

    @AfterEach
    void soltarRegistro() {
        registro.detachAppender(lineas);
    }

    @Test
    @DisplayName("token con forma válida y Keycloak inalcanzable → 401 con el formato común y línea en el registro, sin el token")
    void keycloakInalcanzable_tokenConFormaValida_responde401() throws Exception {
        String token = TokensDePrueba.auxiliar();

        mockMvc.perform(MockMvcRequestBuilders.get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensaje").value(RespuestaErrorSeguridad.MENSAJE_401));

        assertTrue(lineas.list.stream()
                        .anyMatch(l -> l.getFormattedMessage().contains("servicio de claves no disponible")),
                "debe quedar una línea que diga que el servicio de claves no estaba disponible");
        assertFalse(lineas.list.stream().anyMatch(l -> l.getFormattedMessage().contains(token)),
                "el token nunca se registra");
    }
}
