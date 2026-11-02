package com.tedesoft.prestamosservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Servidor real en puertos aleatorios: la API y el Actuator corren en servidores distintos
 * (management.server.port), así que /salud solo existe en el puerto de gestión.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PrestamosServiceApplicationTests {

    @LocalServerPort int puertoApi;
    @LocalManagementPort int puertoGestion;
    @Autowired TestRestTemplate rest;

    @Test
    void contextLoads() { }

    @Test
    void salud_responde200UpEnElPuertoDeGestion() {
        ResponseEntity<Map> respuesta = rest.getForEntity("http://localhost:" + puertoGestion + "/salud", Map.class);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals("UP", respuesta.getBody().get("status"));
    }

    @Test
    void salud_noSeExponeEnElPuertoDeLaApi() {
        assertNotEquals(puertoApi, puertoGestion);

        ResponseEntity<String> respuesta = rest.getForEntity("http://localhost:" + puertoApi + "/salud", String.class);

        assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
    }
}
