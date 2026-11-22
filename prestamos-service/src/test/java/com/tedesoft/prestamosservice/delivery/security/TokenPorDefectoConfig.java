package com.tedesoft.prestamosservice.delivery.security;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Para las pruebas que ya existían (SCRUM-20, CA-14): con la seguridad activa, cada petición lleva por defecto
 * un token del rol que la alcanza. Solicitar una unidad (POST de préstamos) es solo del usuario; todo lo demás
 * se prueba con un auxiliar. La seguridad no se desactiva. Una petición que ponga su propio
 * {@code Authorization} manda sobre este.
 */
@TestConfiguration
public class TokenPorDefectoConfig {

    @Bean
    public MockMvcBuilderCustomizer tokenSegunLaRutaPorDefecto() {
        return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/").with(tokenSegunLaRuta()));
    }

    private static RequestPostProcessor tokenSegunLaRuta() {
        return peticion -> {
            if (peticion.getHeader(HttpHeaders.AUTHORIZATION) == null) {
                boolean solicitud = "POST".equals(peticion.getMethod())
                        && peticion.getRequestURI().endsWith("/prestamos");
                String token = solicitud ? TokensDePrueba.usuario() : TokensDePrueba.auxiliar();
                peticion.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            return peticion;
        };
    }
}
