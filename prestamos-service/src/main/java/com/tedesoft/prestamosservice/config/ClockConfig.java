package com.tedesoft.prestamosservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

/**
 * Único punto donde se lee el reloj del sistema (CA-5).
 * El dominio recibe este {@link Clock} inyectado; las pruebas lo sustituyen por {@code Clock.fixed(...)}.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Bogota");

    @Bean
    public Clock clock() {
        return Clock.system(ZONA_NEGOCIO);
    }
}
