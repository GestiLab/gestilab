package com.tedesoft.prestamosservice.delivery.security;

import com.tedesoft.prestamosservice.delivery.rest.PrestamoRestController;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-20: lo que no tiene regla en {@code SeguridadConfig} se deniega. Así una historia nueva que olvide declarar
 * el rol de su endpoint recibe un 403 en su propia prueba, en lugar de dejarlo abierto a los dos roles.
 */
@WebMvcTest(PrestamoRestController.class)
@Import({SeguridadConfig.class, DecodificadorDePruebaConfig.class})
@DisplayName("SCRUM-20: lo que no tiene regla se deniega en prestamos-service")
class AutorizacionRutaSinReglaTest {

    private static final String BASE = "/api/v1/prestamos-service/prestamos";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPrestamoService prestamoService;

    private void esperar(HttpMethod metodo, String ruta, String token, int codigo) throws Exception {
        var peticion = MockMvcRequestBuilders.request(metodo, ruta);
        if (token != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        var resultado = mockMvc.perform(peticion).andExpect(status().is(codigo));
        resultado.andExpect(jsonPath("$.status").value(codigo));
        resultado.andExpect(jsonPath("$.mensaje").value(
                codigo == 401 ? RespuestaErrorSeguridad.MENSAJE_401 : RespuestaErrorSeguridad.MENSAJE_403));
    }

    @Test
    @DisplayName("una ruta sin regla responde 403 al usuario y al auxiliar, con el formato común")
    void rutaSinRegla_usuarioYAuxiliar_responden403() throws Exception {
        esperar(HttpMethod.GET, BASE + "/ruta-sin-regla/otra", TokensDePrueba.usuario(), 403);
        esperar(HttpMethod.GET, BASE + "/ruta-sin-regla/otra", TokensDePrueba.auxiliar(), 403);
    }

    @Test
    @DisplayName("un método sin regla sobre una ruta que existe también responde 403")
    void metodoSinRegla_usuarioYAuxiliar_responden403() throws Exception {
        esperar(HttpMethod.DELETE, BASE + "/1", TokensDePrueba.usuario(), 403);
        esperar(HttpMethod.DELETE, BASE + "/1", TokensDePrueba.auxiliar(), 403);
    }

    @Test
    @DisplayName("una ruta sin regla y sin token responde 401, no 403")
    void rutaSinRegla_sinToken_responde401() throws Exception {
        esperar(HttpMethod.GET, BASE + "/ruta-sin-regla/otra", null, 401);
    }
}
