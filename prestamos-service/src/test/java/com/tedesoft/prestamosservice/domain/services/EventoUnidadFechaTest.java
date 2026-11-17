package com.tedesoft.prestamosservice.domain.services;

import com.tedesoft.prestamosservice.domain.clients.IEventoUnidadPendiente;
import com.tedesoft.prestamosservice.domain.clients.IInventarioClient;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.repositories.IPrestamoDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RQ-02: la fecha del evento sale del reloj inyectado, no del reloj del sistema. Con un reloj fijo,
 * el evento queda con ESA fecha; si el dominio llamara a {@code OffsetDateTime.now()} la prueba fallaría.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RQ-02: la fecha del evento sale del reloj inyectado")
class EventoUnidadFechaTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final OffsetDateTime AHORA_FIJO = OffsetDateTime.parse("2026-11-20T10:00:00-05:00");

    @Mock IPrestamoDao prestamoDao;
    @Mock IInventarioClient inventarioClient;
    @Mock IEventoUnidadPendiente eventos;

    private PrestamoServiceImpl servicio;

    @BeforeEach
    void configurar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), BOGOTA);
        servicio = new PrestamoServiceImpl(prestamoDao, inventarioClient, new FechaServiceImpl(reloj), eventos,
                (clave, accion) -> accion.get(),
                null);
    }

    private Prestamo prestamo() {
        return Prestamo.solicitar(14L, 2L, "ana", 7, LocalDate.of(2026, 11, 13));
    }

    @Test
    @DisplayName("entregar: el evento PRESTADA lleva la fecha del reloj inyectado")
    void entregar_usaElRelojInyectado() {
        Prestamo p = prestamo();
        when(prestamoDao.buscarParaActualizar(1L)).thenReturn(Optional.of(p));
        when(prestamoDao.save(p)).thenReturn(p);

        servicio.entregar(1L);

        verify(eventos).guardar(eq(14L), eq("PRESTADA"), eq(AHORA_FIJO));
    }

    @Test
    @DisplayName("recibir: el evento DISPONIBLE lleva la fecha del reloj inyectado")
    void recibir_usaElRelojInyectado() {
        Prestamo p = prestamo();
        p.entregar();
        when(prestamoDao.buscarParaActualizar(1L)).thenReturn(Optional.of(p));
        when(prestamoDao.save(p)).thenReturn(p);

        servicio.recibir(1L);

        verify(eventos).guardar(eq(14L), eq("DISPONIBLE"), eq(AHORA_FIJO));
    }
}
