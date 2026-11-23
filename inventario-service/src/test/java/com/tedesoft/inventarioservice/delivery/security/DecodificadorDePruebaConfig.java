package com.tedesoft.inventarioservice.delivery.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Reemplaza al decodificador real (que descarga las claves de Keycloak) por uno que conoce la clave de prueba. */
@TestConfiguration
public class DecodificadorDePruebaConfig {

    @Bean
    @Primary
    public JwtDecoder decodificadorDePrueba() {
        return TokensDePrueba.decodificador();
    }
}
