package com.tedesoft.prestamosservice.delivery.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * Regla de seguridad para los controladores de juguete que algunas pruebas registran en {@code /probe/**} (por
 * ejemplo, para probar el manejador de errores). Como {@code SeguridadConfig} deniega lo que no declara, esas rutas
 * de prueba necesitan su propia regla; no se desactiva la seguridad (SCRUM-20, CA-14): siguen exigiendo un token
 * válido de alguno de los dos roles.
 */
@TestConfiguration
public class RutasDePruebaConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain cadenaDeRutasDePrueba(HttpSecurity http,
                                                     PuntoEntradaAutenticacion puntoEntrada,
                                                     ManejadorAccesoDenegado accesoDenegado) throws Exception {
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(new KeycloakRolesConverter());
        http
                .securityMatcher(antMatcher("/probe/**"))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reglas -> reglas.anyRequest().hasAnyRole(Roles.USUARIO, Roles.AUXILIAR))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(puntoEntrada)
                        .accessDeniedHandler(accesoDenegado))
                .oauth2ResourceServer(recurso -> recurso
                        .authenticationEntryPoint(puntoEntrada)
                        .accessDeniedHandler(accesoDenegado)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidor)));
        return http.build();
    }
}
