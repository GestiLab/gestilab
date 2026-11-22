package com.tedesoft.inventarioservice.delivery.security;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Para las pruebas que ya existían (SCRUM-20, CA-14): con la seguridad activa, cada petición lleva por defecto el
 * token de un auxiliar, que puede todo. La seguridad no se desactiva; esas pruebas solo envían un token de rol
 * correcto. Una petición que ponga su propio {@code Authorization} manda sobre este.
 */
@TestConfiguration
public class TokenPorDefectoConfig {

    @Bean
    public MockMvcBuilderCustomizer tokenDeAuxiliarPorDefecto() {
        return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.auxiliar()));
    }
}
