package com.tedesoft.prestamosservice.domain.services;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** JUnit puro, sin contexto de Spring: el reloj se fija a mano (CA-6). */
class FechaServiceImplTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Test
    void hoy_devuelveLaFechaDelRelojInyectado() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), BOGOTA);
        FechaServiceImpl servicio = new FechaServiceImpl(reloj);

        assertEquals(LocalDate.of(2026, 11, 2), servicio.hoy());
    }

    @Test
    void hoy_usaLaZonaDelRelojYNoUtc() {
        // 03:00 UTC del 3 de noviembre = 22:00 del 2 de noviembre en Bogotá (UTC-5)
        Clock reloj = Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), BOGOTA);
        FechaServiceImpl servicio = new FechaServiceImpl(reloj);

        assertEquals(LocalDate.of(2026, 11, 2), servicio.hoy());
    }
}
